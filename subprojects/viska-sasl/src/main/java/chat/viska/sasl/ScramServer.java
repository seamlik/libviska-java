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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.codec.binary.Base64;

public class ScramServer implements Server {

  private enum State {
    INITIALIZED,
    INITIAL_RESPONSE_RECEIVED,
    CHALLENGE_SENT,
    FINAL_RESPONSE_RECEIVED,
    COMPLETED
  }

  private final ScramMechanism scram;
  private final String initialNounce;
  private final Base64 base64 = new Base64(false);
  private final PropertiesRetriever retriever;
  private String username = "";
  private byte[] saltedPassword;
  private byte[] salt;
  private int iteration = 1;
  private String fullNounce = "";
  private State state = State.INITIALIZED;
  private ScramException error;

  public ScramServer(final ScramMechanism scram,
                     final PropertiesRetriever retriever) {
    Objects.requireNonNull(scram);
    Objects.requireNonNull(retriever);

    this.scram = scram;
    this.retriever = retriever;

    byte[] randomBytes = new byte[12]; // Making a 16-letter nounce
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64.encodeToString(randomBytes);
  }

  private void consumeInitialResponse(final String response) {
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response);
    } catch (ScramException ex) {
      error = ex;
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      state = State.COMPLETED;
      error = new ScramException("extensions-not-supported");
      return;
    }

    // Username
    username = params.get("n");
    if (username == null) {
      username = "";
    }
    if (username.isEmpty()) {
      error = new ScramException("invalid-username");
      return;
    }

    // Nounce
    final String clientNounce = params.get("r");
    if (clientNounce == null || clientNounce.isEmpty()) {
      error = new ScramException("invalid-nounce");
    }
    fullNounce = clientNounce + initialNounce;
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
      random.nextBytes(this.salt);
      iteration = random.nextInt() + 4096;
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
    final Map<String, String> params;
    try {
      params = ScramMechanism.convertMessageToMap(response);
    } catch (ScramException ex) {
      error = ex;
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      state = State.COMPLETED;
      error = new ScramException("extensions-not-supported");
      return;
    }

    // ChannelBinding
    final boolean channelBindingValid = Objects.equals(
        params.get("c"),
        base64.encodeToString(
            ScramMechanism.GS2_HEADER.getBytes(StandardCharsets.UTF_8)
        )
    );
    if (!channelBindingValid) {
      error = new ScramException("channel-binding-not-supported");
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
              this.iteration
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
              this.iteration
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
  public String challenge() {
    switch (state) {
      case INITIAL_RESPONSE_RECEIVED:
        if (error != null) {
          state = State.COMPLETED;
          return "e=" + error.getMessage();
        } else {
          state = State.CHALLENGE_SENT;
          return getChallenge();
        }
      case FINAL_RESPONSE_RECEIVED:
        if (error != null) {
          state = State.COMPLETED;
          return "e=" + error.getMessage();
        } else {
          state = State.COMPLETED;
          return getResult();
        }
      case COMPLETED:
        return "";
      default:
        throw new IllegalStateException();
    }
  }

  @Override
  public void acceptResponse(final String response) {
    switch (state) {
      case INITIALIZED:
        consumeInitialResponse(response);
        state = State.INITIAL_RESPONSE_RECEIVED;
        break;
      case CHALLENGE_SENT:
        consumeFinalResponse(response);
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
  public boolean isCompleted() throws ScramException {
    if (error == null) {
      return state == State.COMPLETED;
    } else {
      throw error;
    }
  }

  @Override
  public Charset getCharset() {
    return StandardCharsets.UTF_8;
  }
}