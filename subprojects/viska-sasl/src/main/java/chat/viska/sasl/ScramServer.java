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
 * SASL server of SCRAM mechanism specified in
 * <a href="https://datatracker.ietf.org/doc/rfc5802">RFC 5802: Salted Challenge
 * Response Authentication Mechanism (SCRAM) SASL and GSS-API Mechanisms</a>.
 * Note that this implementation does not support Channel Binding.
 */
public class ScramServer implements Server {

  private enum State {
    INITIALIZED,
    INITIAL_RESPONSE_RECEIVED,
    CHALLENGE_SENT,
    FINAL_RESPONSE_RECEIVED,
    COMPLETED
  }

  private static final int DEFAULT_ITERATION = 4096;

  private final ScramMechanism scram;
  private final String initialNounce;
  private final Base64 base64 = new Base64(0, new byte[0], false);
  private final PropertiesRetriever retriever;
  private String gs2Header = "";
  private String username = "";
  private byte[] saltedPassword;
  private byte[] salt;
  private int iteration = -1;
  private String authzId = "";
  private String fullNounce = "";
  private State state = State.INITIALIZED;
  private ScramException error;

  /**
   * Constructs a SCRAM server. The data retrieved by the {@code retriever} must
   * contain the following key-value pairs:
   * <ul>
   *   <li>
   *     {@code password} ({@link String}): The raw password in
   *     {@code stringprep}ed form. If this property is present, the server will
   *     generate {@code salted-password} on demand using random {@code salt}
   *     and {@code iteration}.
   *   </li>
   *   <li>
   *     {@code salted-password} ({@link Byte}): Optional if {@code password} is
   *     present.
   *   </li>
   *   <li>
   *     {@code salt} ({@link Byte}): Optional if {@code password} is present.
   *   </li>
   *   <li>
   *     {@code iteration} ({@link Integer}): Optional if {@code password} is
   *     present.
   *   </li>
   * </ul>
   * @param retriever Method to retrieve sensitive data for the authentication.
   * @throws NullPointerException if either of the parameters are {@code null}.
   */
  public ScramServer(final ScramMechanism scram,
                     final PropertiesRetriever retriever) {
    Objects.requireNonNull(scram, "`scram` is absent.");
    Objects.requireNonNull(retriever, "`retriever` is absent.");

    this.scram = scram;
    this.retriever = retriever;

    byte[] randomBytes = new byte[12]; // Making a 16-letter nounce
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64.encodeToString(randomBytes).trim();
  }

  private void consumeInitialResponse(final String response) {
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response, true);
    } catch (ScramException ex) {
      this.error = ex;
      this.state = State.COMPLETED;
      return;
    }

    // gs2-header
    this.gs2Header = params.get("gs2-header");
    if (this.gs2Header == null) {
      this.gs2Header = "";
    }
    if (gs2Header.isEmpty()) {
      this.error = new ScramException("invalid-syntax");
      this.state = State.COMPLETED;
      return;
    }

    // Channel Binding
    if (!Objects.equals(params.get("gs2-cbind-flag"), "n")) {
      this.error = new ScramException("channel-binding-not-supported");
      this.state = State.COMPLETED;
      return;
    }

    // Authorization ID
    this.authzId = params.get("a");
    if (this.authzId == null) {
      this.authzId = "";
    }

    // Extension
    if (params.containsKey("m")) {
      this.error = new ScramException("extensions-not-supported");
      this.state = State.COMPLETED;
      return;
    }

    // Username
    this.username = params.get("n");
    this.username = this.username == null
        ? ""
        : this.username
            .replace("=2C", ",")
            .replace("=3D", "=");
    if (username.isEmpty()) {
      this.error = new ScramException("invalid-username");
      this.state = State.COMPLETED;
      return;
    }

    // Nounce
    final String clientNounce = params.get("r");
    if (clientNounce == null || clientNounce.isEmpty()) {
      this.error = new ScramException("invalid-nounce");
      this.state = State.COMPLETED;
    }
    this.fullNounce = clientNounce + this.initialNounce;
  }

  private String getChallenge() {
    // Retrieving sensitive properties
    final Map<String, ?> properties;
    try {
      properties = retriever.retrieve(username, getMechanism());
    } catch (Exception ex) {
      error = new ScramException(ex);
      state = State.COMPLETED;
      return "";
    }
    if (properties.isEmpty()) {
      error = new ScramException("unknown-user");
      state = State.COMPLETED;
      return "";
    }
    final String password = (String) properties.get("password");
    if (password == null || password.isEmpty()) {
      final byte[] saltedPassword = (byte[]) properties.get("salted-password");
      this.saltedPassword = saltedPassword == null ? new byte[0] : saltedPassword;
      final byte[] salt = (byte[]) properties.get("salt");
      this.salt = salt == null ? new byte[0] : salt;
      final Integer iteration = (Integer) properties.get("iteration");
      this.iteration = iteration == null ? -1 : iteration;
    } else {
      final SecureRandom random = new SecureRandom();
      this.salt = new byte[64 / 8];
      random.nextBytes(this.salt);
      this.iteration = DEFAULT_ITERATION;
      try {
        this.saltedPassword = scram.getSaltedPassword(password, this.salt, this.iteration);
      } catch (InvalidKeyException ex) {
        throw new RuntimeException(ex);
      }
    }

    return String.format(
        "r=%1s,s=%2s,i=%3s",
        this.fullNounce,
        base64.encodeToString(this.salt),
        this.iteration
    );
  }

  private void consumeFinalResponse(String response) {
    // This method does not set the state to COMPLETED because the server has a
    // chance to send the error back to the client.

    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response, false);
    } catch (ScramException ex) {
      error = ex;
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      error = new ScramException("extensions-not-supported");
      return;
    }

    // ChannelBinding
    final boolean channelBindingValid = Objects.equals(
        params.get("c"),
        base64.encodeToString(this.gs2Header.getBytes(StandardCharsets.UTF_8))
    );
    if (!channelBindingValid) {
      error = new ScramException("channel-bindings-dont-match");
    }

    // Nounce
    if (!Objects.equals(params.get("r"), this.fullNounce)) {
      error = new ScramException("invalid-nounce");
      return;
    }

    // Proof
    final byte[] clientProof;
    try {
      final byte[] clientKey = scram.getClientKey(this.saltedPassword);
      final byte[] storedKey = scram.getStoredKey(clientKey);
      final byte[] clientSig = scram.getClientSignature(
          storedKey,
          ScramMechanism.getAuthMessage(
              this.fullNounce.replace(initialNounce, ""),
              this.fullNounce,
              this.username,
              this.salt,
              this.iteration,
              this.gs2Header
          )
      );
      clientProof = Bytes.xor(clientKey,clientSig);
    } catch (InvalidKeyException ex) {
      throw new RuntimeException(ex);
    }
    if (!Arrays.equals(clientProof, base64.decode(params.get("p")))) {
      error = new ScramException("invalid-proof");
    }
  }

  private String getResult() {
    try {
      return "v=" + base64.encodeToString(scram.getServerSignature(
          scram.getServerKey(this.saltedPassword),
          ScramMechanism.getAuthMessage(
              this.fullNounce.replace(initialNounce, ""),
              this.fullNounce,
              this.username,
              this.salt,
              this.iteration,
              this.gs2Header
          )
      ));
    } catch (InvalidKeyException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public String getMechanism() {
    return "SCRAM-" + scram.getAlgorithm();
  }

  @Override
  public byte[] challenge() {
    switch (state) {
      case INITIAL_RESPONSE_RECEIVED:
        state = State.CHALLENGE_SENT;
        return getChallenge().getBytes(StandardCharsets.UTF_8);
      case FINAL_RESPONSE_RECEIVED:
        if (error != null) {
          state = State.COMPLETED;
          return ("e=" + error.getMessage()).getBytes(StandardCharsets.UTF_8);
        } else {
          state = State.COMPLETED;
          return getResult().getBytes(StandardCharsets.UTF_8);
        }
      default:
        throw new IllegalStateException("Not about to challenge.");
    }
  }

  @Override
  public void acceptResponse(final byte[] response) {
    switch (state) {
      case INITIALIZED:
        consumeInitialResponse(new String(response, StandardCharsets.UTF_8));
        state = State.INITIAL_RESPONSE_RECEIVED;
        break;
      case CHALLENGE_SENT:
        consumeFinalResponse(new String(response, StandardCharsets.UTF_8));
        state = State.FINAL_RESPONSE_RECEIVED;
        break;
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public boolean isServerFirst() {
    return false;
  }

  @Override
  public boolean isCompleted() {
    return state == State.COMPLETED;
  }

  @Override
  public ScramException getError() {
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
   *   <li>{@code username} ({@link String})</li>
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
    result.put("username", this.username);
    result.put("iteration", this.iteration);
    return result;
  }

  @Override
  public String getAuthorizationId() {
    if (this.state != State.COMPLETED) {
      return null;
    } else {
      return authzId.isEmpty() ? username : authzId;
    }
  }
}