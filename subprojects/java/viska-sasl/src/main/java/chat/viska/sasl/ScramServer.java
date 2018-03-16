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

import java.io.IOException;
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
import java.util.Optional;
import javax.annotation.concurrent.NotThreadSafe;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * SASL server of <a href="https://datatracker.ietf.org/doc/rfc5802">SCRAM
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
@NotThreadSafe
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
  private final Base64.Decoder base64Decoder = Base64.getDecoder();
  private final Base64.Encoder base64Encoder = Base64.getEncoder();
  private final CredentialRetriever retriever;
  private final Map<String, Object> properties = new HashMap<>();
  private String gs2Header = "";
  private String username = "";
  private byte[] saltedPassword = new byte[0];
  private byte[] salt = new byte[0];
  private int iteration = -1;
  private String authzId = "";
  private String fullNounce = "";
  private State state = State.INITIALIZED;
  private @Nullable AuthenticationException error;

  /**
   * Constructs a SCRAM server. The data retrieved by the {@code retriever} must
   * contain the following key-value pairs:
   * <ul>
   *   <li>
   *     {@link ScramMechanism#KEY_PASSWORD}: If this is provided, the server generates a
   *     salted password on demand using a random salt and iteration.
   *   </li>
   *   <li>
   *     {@link ScramMechanism#KEY_SALTED_PASSWORD}: Optional if a password is provided.
   *   </li>
   *   <li>
   *     {@link ScramMechanism#KEY_SALT}: Optional if a password is provided.
   *   </li>
   *   <li>
   *     {@link ScramMechanism#KEY_ITERATION}: Optional if a passworda is provided.
   *   </li>
   * </ul>
   */
  public ScramServer(final ScramMechanism scram,
                     final CredentialRetriever retriever) {
    this.scram = scram;
    this.retriever = retriever;

    byte[] randomBytes = new byte[12]; // Making a 16-letter nounce
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64Encoder.encodeToString(randomBytes);
  }

  private void generateSaltedPassword()
      throws IOException, AbortedException, AuthenticationException, InvalidKeySpecException {
    final String password = (String) retriever.retrieve(
        username, getMechanism(), ScramMechanism.KEY_PASSWORD
    );
    if (password == null) {
      throw new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED
      );
    }
    this.iteration = DEFAULT_ITERATION;
    final SecureRandom random = new SecureRandom();
    this.salt = new byte[64 / 8];
    random.nextBytes(this.salt);
    this.saltedPassword = scram.getSaltedPassword(password, this.salt, this.iteration);
  }

  private void consumeInitialResponse(final String response) throws AuthenticationException {
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response, true);
    } catch (Exception ex) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
      this.state = State.COMPLETED;
      throw error;
    }

    // gs2-header
    this.gs2Header = Optional.ofNullable(params.get("gs2-header")).orElse("");
    if (gs2Header.isEmpty()) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
      this.state = State.COMPLETED;
      throw error;
    }

    // Channel Binding
    if (!Objects.equals(params.get("gs2-cbind-flag"), "n")) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Channel Binding not supported."
      );
      this.state = State.COMPLETED;
      return;
    }

    // Authorization ID
    this.authzId = Optional.ofNullable(params.get("a")).orElse("");

    // Extension
    if (params.containsKey("m")) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Extension not supported."
      );
      this.state = State.COMPLETED;
      return;
    }

    // Username
    this.username = Optional.ofNullable(params.get("n")).map(
      it -> it.replace("=2C", ",").replace("=3D", "=")
    ).orElse("");
    if (username.isEmpty()) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid username."
      );
      this.state = State.COMPLETED;
      throw error;
    }

    // Nounce
    final String clientNounce = params.get("r");
    if (clientNounce == null || clientNounce.isEmpty()) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Empty nounce."
      );
      this.state = State.COMPLETED;
    }
    this.fullNounce = clientNounce + this.initialNounce;
  }

  private String getChallenge() {
    try {
      final byte[] saltedPassword = (byte[]) retriever.retrieve(
          this.username,
          getMechanism(),
          ScramMechanism.KEY_SALTED_PASSWORD
      );
      if (saltedPassword != null) {
        final byte[] salt = (byte[]) retriever.retrieve(
            username, getMechanism(), ScramMechanism.KEY_SALT
        );
        final Integer iteration = (Integer) retriever.retrieve(
            username, getMechanism(), ScramMechanism.KEY_ITERATION
        );
        if (salt != null && iteration != null) {
          this.saltedPassword = saltedPassword;
          this.salt = salt;
          this.iteration = iteration;
        } else {
          generateSaltedPassword();
        }
      } else {
        generateSaltedPassword();
      }
    } catch (AuthenticationException ex) {
      this.error = ex;
      this.state = State.COMPLETED;
      return "";
    } catch (Exception ex) {
      this.error = new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
          ex
      );
      this.state = State.COMPLETED;
      return "";
    }

    return String.format(
        "r=%1s,s=%2s,i=%3s",
        this.fullNounce,
        base64Encoder.encodeToString(this.salt),
        this.iteration
    );
  }

  private void consumeFinalResponse(String response) throws AuthenticationException {
    // This method does not set the state to COMPLETED because the server has a
    // chance to send the error back to the client.

    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response, false);
    } catch (Exception ex) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Invalid syntax."
      );
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Extension not supported."
      );
      return;
    }

    // ChannelBinding
    final boolean channelBindingValid = Objects.equals(
        params.get("c"),
        base64Encoder.encodeToString(this.gs2Header.getBytes(StandardCharsets.UTF_8))
    );
    if (!channelBindingValid) {
      error = new AuthenticationException(
          AuthenticationException.Condition.MALFORMED_REQUEST,
          "Channel Binding not supported."
      );
    }

    // Nounce
    if (!Objects.equals(params.get("r"), this.fullNounce)) {
      error = new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
          "Nounce does not match."
      );
      return;
    }

    // Proof
    final byte[] clientProof;
    try {
      final byte[] clientKey = scram.getClientKey(this.saltedPassword);
      final byte[] storedKey = scram.getStoredKey(clientKey);
      final byte[] clientSig = scram.getClientSignature(
          storedKey,
          this.scram.getAuthMessage(
              this.fullNounce.replace(initialNounce, ""),
              this.fullNounce,
              this.username,
              this.salt,
              this.iteration,
              this.gs2Header
          )
      );
      clientProof = ByteUtils.xor(clientKey,clientSig);
    } catch (InvalidKeyException ex) {
      throw new RuntimeException(ex);
    }
    final @Nullable String proofParam = params.get("p");
    if (proofParam == null) {
      error = new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
          "Client proof not received."
      );
      throw error;
    }
    if (!Arrays.equals(clientProof, base64Decoder.decode(proofParam))) {
      error = new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED,
          "Client proof incorrect."
      );
      throw error;
    }
  }

  private String getResult() {
    try {
      return "v=" + base64Encoder.encodeToString(scram.getServerSignature(
          this.scram.getServerKey(this.saltedPassword),
          this.scram.getAuthMessage(
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

  private void prepareNegotiatedProperties() {
    this.properties.put(ScramMechanism.KEY_SALT, this.salt);
    this.properties.put(ScramMechanism.KEY_SALTED_PASSWORD, this.saltedPassword);
    this.properties.put(ScramMechanism.KEY_ITERATION, this.iteration);
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
          prepareNegotiatedProperties();
          return getResult().getBytes(StandardCharsets.UTF_8);
        }
      default:
        throw new IllegalStateException(
            "Must not challenge before accepting a response."
        );
    }
  }

  @Override
  public void acceptResponse(final byte[] response) throws AuthenticationException {
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
        throw new IllegalStateException(
            "Must not accept another response before challenging."
        );
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
    return Collections.unmodifiableMap(properties);
  }

  @Override
  public String getAuthorizationId() {
    if (this.state != State.COMPLETED) {
      throw new IllegalStateException("Authentication not completed.");
    } else {
      return authzId.isEmpty() ? username : authzId;
    }
  }
}