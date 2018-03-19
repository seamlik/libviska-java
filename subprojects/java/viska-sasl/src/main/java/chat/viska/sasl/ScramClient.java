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
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  private @Nullable AuthenticationException error;

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

  private void consumeChallenge(final String challenge) throws AuthenticationException {
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(challenge, false);
    } catch (Exception ex) {
      throw new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          ex.getLocalizedMessage()
      );
    }

    // Extension
    if (params.containsKey("m")) {
      throw error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Extension not supported."
      );
    }

    // Nounce
    final String serverNounce = params.get("r");
    if (serverNounce == null || serverNounce.isEmpty()) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Empty nounce."
      );
      throw error;
    }
    if (!serverNounce.startsWith(initialNounce)) {
      error = new AuthenticationException(
          AuthenticationException.Condition.SERVER_NOT_AUTHORIZED,
          "Server nounce does not match."
      );
      return;
    }
    fullNounce = serverNounce;

    // Salt
    final @Nullable String saltParam = params.get("s");
    if (saltParam == null) {
      throw new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Salt not received."
      );
    }
    try {
      this.salt = base64Decoder.decode(saltParam);
    } catch (IllegalArgumentException ex) {
      throw error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid salt."
      );
    }
    if (this.salt.length == 0) {
      throw error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Empty salt."
      );
    }

    // Iteration
    final @Nullable String iterationParam = params.get("i");
    if (iterationParam == null) {
      throw error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Iteration not received."
      );
    }
    try {
      this.iteration = Integer.parseInt(iterationParam);
    } catch (NumberFormatException ex) {
      throw error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid iteration."
      );
    }
    if (this.iteration < 1) {
      throw new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Iteration negative."
      );
    }
  }

  private String getFinalResponse() throws AuthenticationException {

    // Retrieving password or salted-password
    try {
      final Object retrievedSaltedPassword = retriever.retrieve(
          authnId,
          getMechanism(),
          "salted-password"
      );
      final Object retrievedPassword = retriever.retrieve(authnId, getMechanism(), "password");
      final Object retrievedSalt = retriever.retrieve(
          authnId,
          getMechanism(),
          ScramMechanism.KEY_SALT
      );
      final Object retrievedIteration = retriever.retrieve(
          authnId,
          getMechanism(),
          ScramMechanism.KEY_ITERATION
      );

      final boolean saltMatched = retrievedSalt instanceof byte[]
          && Arrays.equals((byte[]) retrievedSalt, salt)
          && Objects.equals(retrievedIteration, iteration);

      if (retrievedPassword instanceof String) {
        saltedPassword = scram.getSaltedPassword((String) retrievedPassword, salt, iteration);
      } else if (retrievedSaltedPassword instanceof byte[] && saltMatched){
        saltedPassword = (byte[]) retrievedSaltedPassword;
      }
      if (saltedPassword == null) {
        throw new AuthenticationException(
            AuthenticationException.Condition.CREDENTIALS_NOT_FOUND
        );
      }
    } catch (InvalidKeySpecException ex) {
      throw new RuntimeException(ex);
    } catch (SecurityException ex) {
      throw new AuthenticationException(
          AuthenticationException.Condition.CREDENTIALS_NOT_FOUND,
          ex
      );
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

  private void consumeResult(final String result) throws AuthenticationException {
    final String[] pair = result.split(",")[0].split("=");
    if (pair.length != 2) {
      throw new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
    }
    switch (pair[0]) {
      case "e":
        throw new AuthenticationException(
            AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
            pair[1]
        );
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
          throw new AuthenticationException(
              AuthenticationException.Condition.SERVER_NOT_AUTHORIZED
          );
        }
      default:
        throw new AuthenticationException(
            AuthenticationException.Condition.MALFORMED_REQUEST,
            "Invalid syntax."
        );
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

  @Override
  public String getMechanism() {
    return "SCRAM-" + scram.getAlgorithm();
  }

  @Override
  public byte[] respond() throws AuthenticationException {
    try {
      switch (state) {
        case INITIALIZED: {
          final byte[] response = getInitialResponse().getBytes(StandardCharsets.UTF_8);
          state = State.INITIAL_RESPONSE_SENT;
          return response;
        }
        case CHALLENGE_RECEIVED: {
          final byte[] response = getFinalResponse().getBytes(StandardCharsets.UTF_8);
          state = State.FINAL_RESPONSE_SENT;
          return response;
        }
        default:
          throw new IllegalStateException("Not about to respond.");
      }
    } catch (AuthenticationException ex) {
      error = ex;
      throw ex;
    }

  }

  @Override
  public void acceptChallenge(final byte[] challenge) {
    try {
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
    } catch (AuthenticationException ex) {
      state = State.COMPLETED;
      error = ex;
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

  @Override
  public Map<String, ?> getNegotiatedProperties() {
    if (!isCompleted()) {
      throw new IllegalStateException("Authentication not completed.");
    }
    return Collections.unmodifiableMap(properties);
  }
}