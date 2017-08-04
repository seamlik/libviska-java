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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.binary.Base64;

/**
 * SASL client of <a href="https://datatracker.ietf.org/doc/rfc5802">SCRAM
 * Mechanism</a>. Note that this implementation does not support Channel
 * Binding.
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
  private final Base64 base64 = new Base64(0, new byte[0], false);
  private final String username;
  private final String initialNounce;
  private final String authzId;
  private final CredentialRetriever retriever;
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
        ScramMechanism.getClientFirstMessageBare(username, initialNounce)
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
      this.salt = base64.decode(params.get("s"));
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
          username,
          getMechanism(),
          "salted-password"
      );
      if (saltedPassword != null) {
        final byte[] salt = (byte[]) retriever.retrieve(
            username, getMechanism(), "salt"
        );
        final int iteration = (Integer) retriever.retrieve(
            username, getMechanism(), "iteration"
        );
        if (Arrays.equals(salt, this.salt) && Objects.equals(iteration, this.iteration)) {
          this.saltedPassword = saltedPassword;
        } else {
          this.saltedPassword = scram.getSaltedPassword(
              (String) retriever.retrieve(
                  username, getMechanism(), "password"
              ),
              this.salt,
              this.iteration
          );
        }
      } else {
        this.saltedPassword = scram.getSaltedPassword(
            (String) retriever.retrieve(
                username, getMechanism(), "password"
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
          ScramMechanism.getAuthMessage(
              this.initialNounce,
              this.fullNounce,
              this.username,
              this.salt,
              this.iteration,
              getGs2Header()
          )
      );
      clientProof = Bytes.xor(clientKey,clientSig);
    } catch (InvalidKeyException ex) {
      throw new RuntimeException(ex);
    }
    return String.format(
        "%1s,p=%2s",
        ScramMechanism.getClientFinalMessageWithoutProof(fullNounce, getGs2Header()),
        base64.encodeToString(clientProof)
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
          serverSig = scram.getServerSignature(
              scram.getServerKey(saltedPassword),
              ScramMechanism.getAuthMessage(
                  initialNounce,
                  fullNounce,
                  username,
                  salt,
                  iteration,
                  getGs2Header()
              )
          );
        } catch (InvalidKeyException ex) {
          throw new RuntimeException(ex);
        }
        if (Arrays.equals(serverSig, base64.decode(pair[1]))) {
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

  /**
   * Default constructor.
   * @param scram Required.
   * @param authnId Authentication ID, required.
   * @param authzId Authorization ID, optional.
   * @param retriever Required.
   * @throws NullPointerException If any required parameters are {@code null}.
   * @throws IllegalArgumentException If {@code authnId} is empty.
   */
  public ScramClient(final ScramMechanism scram,
                     final String authnId,
                     final String authzId,
                     final CredentialRetriever retriever) {
    this.scram = scram;
    this.username = authnId == null ? "" : authnId;
    this.authzId = authzId == null ? "" : authzId;
    this.retriever = retriever;

    Objects.requireNonNull(scram, "`scram` is absent.");
    Objects.requireNonNull(retriever, "`retriever` is absent.");
    if (this.username.isEmpty()) {
      throw new IllegalArgumentException("`authnId` is absent.");
    }

    byte[] randomBytes = new byte[6];
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64.encodeToString(randomBytes).trim();
  }

  @Override
  public String getMechanism() {
    return "SCRAM-" + scram.getAlgorithm();
  }

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

  @Override
  public AuthenticationException getError() {
    if (!isCompleted()) {
      throw new IllegalStateException("Authentication not completed.");
    }
    return error;
  }

  /**
   * Gets a {@link Map} containing properties negotiated during the
   * authentication. It will contain the following key-value pairs:
   * <ul>
   *   <li>{@code salted-password} ({@link Byte})</li>
   *   <li>{@code salt} ({@link Byte})</li>
   *   <li>{@code iteration} ({@link Integer})</li>
   * </ul>
   * @return {@code null} if authentication not completed, otherwise a
   *         {@link Map} which is either empty or containing properties.
   */
  @Override
  public Map<String, ?> getNegotiatedProperties() {
    if (this.state != State.COMPLETED) {
      return null;
    }
    final Map<String, Object> result = new HashMap<>();
    result.put("salt", this.salt);
    result.put("salted-password", this.saltedPassword);
    result.put("iteration", this.iteration);
    return result;
  }
}