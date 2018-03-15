/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.sasl;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * SASL client of <a href="https://datatracker.ietf.org/doc/rfc5802">SCRAM
 * Mechanism</a>. Note that this implementation does not support Channel
 * Binding.
 *
 * <p>The keys contained by the negotiated properties include:</p>
 *
 * <ul>
 *   <li>{@link ScramMechanism#KEY_ITERATION}</li>
 *   <li>{@link ScramMechanism#KEY_SALTED_PASSWORD}</li>
 *   <li>{@link ScramMechanism#KEY_SALT}</li>
 * </ul>
 */
public class ScramClient implements Client {

  private enum State {
    INITIALIZED,
    INITIAL_RESPONSE_SENT,
    CHALLENGE_RECEIVED,
    FINAL_RESPONSE_SENT,
    COMPLETED
  }

  private final ScramMechanism scram;
  private final Base64.Encoder base64Encoder = Base64.getEncoder();
  private final Base64.Decoder base64Decoder = Base64.getDecoder();
  private final String authnId;
  private final String authzId;
  private final String initialNounce;
  private final CredentialRetriever retriever;
  private final Map<String, Object> properties = new HashMap<>();
  private State state = State.INITIALIZED;
  private String fullNounce = "";
  private byte[] saltedPassword = new byte[0];
  private byte[] salt = new byte[0];
  private int iteration = -1;
  private AuthenticationException error;

  private String getGs2Header() {
    final StringBuilder result = new StringBuilder("n,");
    if (!authzId.isEmpty()) {
      result.append("a=").append(authzId);
    }
    result.append(',');
    return result.toString();
  }

  private String getInitialResponse() {
    return String.format(
        "%1s%2s",
        getGs2Header(),
        ScramMechanism.getClientFirstMessageBare(authnId, initialNounce)
    );
  }

  private void consumeChallenge(final String challenge) {
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(challenge, false);
    } catch (Exception ex) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
      state = State.COMPLETED;
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Extension not supported."
      );
      return;
    }

    // Nounce
    final String serverNounce = params.get("r");
    if (serverNounce == null || serverNounce.isEmpty()) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Empty nounce."
      );
      return;
    }
    if (!serverNounce.startsWith(initialNounce)) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.SERVER_NOT_AUTHORIZED,
          "Server nounce does not match."
      );
      return;
    }
    fullNounce = serverNounce;

    // Salt
    try {
      this.salt = base64Decoder.decode(params.get("s"));
    } catch (Exception ex) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid salt."
      );
      return;
    }
    if (this.salt.length == 0) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Empty salt."
      );
      return;
    }

    // Iteration
    try {
      this.iteration = Integer.parseInt(params.get("i"));
    } catch (Exception ex) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid iteration."
      );
      return;
    }
    if (this.iteration < 1) {
      state = State.COMPLETED;
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid iteration."
      );
    }
  }

  private String getFinalResponse() {

    // Retrieving password or salted-password
    try {
      final byte[] saltedPassword = (byte[]) retriever.retrieve(
          authnId,
          getMechanism(),
          "salted-password"
      );
      if (saltedPassword != null) {
        final byte[] salt = (byte[]) retriever.retrieve(
            authnId, getMechanism(), ScramMechanism.KEY_SALT
        );
        final int iteration = (Integer) retriever.retrieve(
            authnId, getMechanism(), ScramMechanism.KEY_ITERATION
        );
        if (Arrays.equals(salt, this.salt) && Objects.equals(iteration, this.iteration)) {
          this.saltedPassword = saltedPassword;
        } else {
          this.saltedPassword = scram.getSaltedPassword(
              (String) retriever.retrieve(
                  authnId, getMechanism(), "password"
              ),
              this.salt,
              this.iteration
          );
        }
      } else {
        this.saltedPassword = scram.getSaltedPassword(
            (String) retriever.retrieve(
                authnId, getMechanism(), "password"
            ),
            this.salt,
            this.iteration
        );
      }
      if (this.saltedPassword == null) {
        throw new AuthenticationException(
            AuthenticationException.Condition.CREDENTIALS_NOT_FOUND
        );
      }
    } catch (AuthenticationException ex) {
      this.error = ex;
      return "";
    } catch (AbortedException ex) {
      this.error = new AuthenticationException(
          AuthenticationException.Condition.ABORTED
      );
    } catch (Exception ex) {
      this.error = new AuthenticationException(
          AuthenticationException.Condition.CREDENTIALS_NOT_FOUND,
          ex
      );
      return "";
    }

    // Calculating proof
    final byte[] clientProof;
    try {
      final byte[] clientKey = this.scram.getClientKey(this.saltedPassword);
      final byte[] storedKey = this.scram.getStoredKey(clientKey);
      final byte[] clientSig = this.scram.getClientSignature(
          storedKey,
          this.scram.getAuthMessage(
              this.initialNounce,
              this.fullNounce,
              this.authnId,
              this.salt,
              this.iteration,
              getGs2Header()
          )
      );
      clientProof = ByteUtils.xor(clientKey,clientSig);
    } catch (InvalidKeyException ex) {
      throw new RuntimeException(ex);
    }
    return String.format(
        "%1s,p=%2s",
        this.scram.getClientFinalMessageWithoutProof(fullNounce, getGs2Header()),
        base64Encoder.encodeToString(clientProof)
    );
  }

  private void consumeResult(final String result) {
    final String[] pair = result.split(",")[0].split("=");
    if (pair.length != 2) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
      return;
    }
    switch (pair[0]) {
      case "e":
        error = new AuthenticationException(
            AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
            pair[1]
        );
        break;
      case "v":
        final byte[] serverSig;
        try {
          serverSig = this.scram.getServerSignature(
              this.scram.getServerKey(this.saltedPassword),
              this.scram.getAuthMessage(
                  this.initialNounce,
                  this.fullNounce,
                  this.authnId,
                  this.salt,
                  this.iteration,
                  getGs2Header()
              )
          );
        } catch (InvalidKeyException ex) {
          throw new RuntimeException(ex);
        }
        if (Arrays.equals(serverSig, base64Decoder.decode(pair[1]))) {
          break;
        } else {
          error = new AuthenticationException(
              AuthenticationException.Condition.SERVER_NOT_AUTHORIZED
          );
          break;
        }
      default:
        error = new AuthenticationException(
            AuthenticationException.Condition.MALFORMED_REQUEST,
            "Invalid syntax."
        );
        break;
    }
  }

  private void prepareNegotiatedProperties() {
    this.properties.put(ScramMechanism.KEY_SALT, this.salt);
    this.properties.put(ScramMechanism.KEY_SALTED_PASSWORD, this.saltedPassword);
    this.properties.put(ScramMechanism.KEY_ITERATION, this.iteration);
  }

  /**
   * Default constructor.
   * @param scram Required.
   * @param authnId Authentication ID, required.
   * @param authzId Authorization ID, optional.
   * @param retriever Required.
   * @throws IllegalArgumentException If {@code authnId} is empty.
   */
  public ScramClient(final ScramMechanism scram,
                     final String authnId,
                     @Nullable final String authzId,
                     final CredentialRetriever retriever) {
    this.scram = scram;
    this.authnId = authnId;
    this.authzId = authzId == null ? "" : authzId;
    this.retriever = retriever;
    if (this.authnId.isEmpty()) {
      throw new IllegalArgumentException("`authnId` is absent.");
    }

    byte[] randomBytes = new byte[6];
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64Encoder.encodeToString(randomBytes).trim();
  }

  @Nonnull
  @Override
  public String getMechanism() {
    return "SCRAM-" + scram.getAlgorithm();
  }

  @Nullable
  @Override
  public byte[] respond() {
    switch (state) {
      case INITIALIZED:
        state = State.INITIAL_RESPONSE_SENT;
        return getInitialResponse().getBytes(StandardCharsets.UTF_8);
      case CHALLENGE_RECEIVED:
        state = State.FINAL_RESPONSE_SENT;
        return getFinalResponse().getBytes(StandardCharsets.UTF_8);
      default:
        throw new IllegalStateException("Not about to respond.");
    }
  }

  @Override
  public void acceptChallenge(final byte[] challenge) {
    switch (state) {
      case INITIAL_RESPONSE_SENT:
        consumeChallenge(new String(challenge, StandardCharsets.UTF_8));
        state = State.CHALLENGE_RECEIVED;
        break;
      case FINAL_RESPONSE_SENT:
        consumeResult(new String(challenge, StandardCharsets.UTF_8));
        state = State.COMPLETED;
        prepareNegotiatedProperties();
        break;
      default:
        throw new IllegalStateException("Not waiting for a challenge.");
    }
  }

  @Override
  public boolean isClientFirst() {
    return true;
  }

  @Override
  public boolean isCompleted() {
    return state == State.COMPLETED;
  }

  @Nullable
  @Override
  public AuthenticationException getError() {
    if (!isCompleted()) {
      throw new IllegalStateException("Authentication not completed.");
    }
    return error;
  }

  @Nonnull
  @Override
  public Map<String, ?> getNegotiatedProperties() {
    if (!isCompleted()) {
      throw new IllegalStateException("Authentication not completed.");
    }
    return Collections.unmodifiableMap(properties);
  }
}