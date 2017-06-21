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
 * SASL client of SCRAM mechanism specified in
 * <a href="https://datatracker.ietf.org/doc/rfc5802">RFC 5802: Salted Challenge
 * Response Authentication Mechanism (SCRAM) SASL and GSS-API Mechanisms</a>.
 * Note that this implementation does not support Channel Binding.
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
  private final Base64 base64 = new Base64(false);
  private final String username;
  private final String password;
  private final String initialNounce;
  private final String authzId;
  private State state = State.INITIALIZED;
  private String fullNounce = "";
  private byte[] saltedPassword = new byte[0];
  private byte[] salt = new byte[0];
  private int iteration = -1;
  private ScramException error;

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
    } catch (ScramException ex) {
      error = ex;
      state = State.COMPLETED;
      return;
    }

    // Extension
    if (params.containsKey("m")) {
      state = State.COMPLETED;
      error = new ScramException("extensions-not-supported");
      return;
    }

    // Nounce
    final String serverNounce = params.get("r");
    if (serverNounce == null || serverNounce.isEmpty()) {
      state = State.COMPLETED;
      error = new ScramException("invalid-nounce");
      return;
    }
    if (!serverNounce.startsWith(initialNounce)) {
      state = State.COMPLETED;
      error = new ScramException("invalid-nounce");
      return;
    }
    fullNounce = serverNounce;

    // Salt
    if (!params.containsKey("s")) {
      state = State.COMPLETED;
      error = new ScramException("invalid-salt");
      return;
    }
    final byte[] serverSalt;
    try {
      serverSalt = base64.decode(params.get("s"));
    } catch (Exception ex) {
      state = State.COMPLETED;
      error = new ScramException("invalid-salt", ex);
      return;
    }
    if (serverSalt.length == 0) {
      state = State.COMPLETED;
      error = new ScramException("invalid-salt");
      return;
    }
    if (salt.length != 0 && !Arrays.equals(serverSalt, salt)) {
      state = State.COMPLETED;
      error = new ScramException("invalid-salt");
      return;
    } else {
      salt = serverSalt;
    }

    // Iteration
    if (!params.containsKey("i")) {
      state = State.COMPLETED;
      error = new ScramException("invalid-iteration");
      return;
    }
    final int serverIteration;
    try {
      serverIteration = Integer.parseInt(params.get("i"));
    } catch (Exception ex) {
      state = State.COMPLETED;
      error = new ScramException("invalid-iteration");
      return;
    }
    if (serverIteration < 1) {
      state = State.COMPLETED;
      error = new ScramException("invalid-iteration");
      return;
    }
    if (iteration >= 1 && !(iteration == serverIteration)) {
      state = State.COMPLETED;
      error = new ScramException("invalid-iteration");
    } else {
      iteration = serverIteration;
    }
  }

  private String getFinalResponse() {
    final byte[] clientProof;
    try {
      if (saltedPassword.length == 0) {
        saltedPassword = scram.getSaltedPassword(password, salt, iteration);
      }
      final byte[] clientKey = scram.getClientKey(saltedPassword);
      final byte[] storedKey = scram.getStoredKey(clientKey);
      final byte[] clientSig = scram.getClientSignature(
          storedKey,
          ScramMechanism.getAuthMessage(
              initialNounce,
              fullNounce,
              username,
              salt,
              iteration,
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
      error = new ScramException("invalid-syntax");
      return;
    }
    switch (pair[0]) {
      case "e":
        error = new ScramException(pair[1]);
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
          error = new ScramException("server-signature-incorrect");
          break;
        }
      default:
        error = new ScramException("invalid-syntax");
        break;
    }
  }

  public ScramClient(final ScramMechanism scram,
                     final String username,
                     final String password,
                     final String authzId) {
    this.scram = scram;
    this.username = username == null ? "" : username;
    this.password = password == null ? "" : password;
    this.authzId = authzId == null ? "" : authzId;

    Objects.requireNonNull(scram);
    if (this.username.isEmpty()) {
      throw new IllegalArgumentException("`username` is absent.");
    }
    if (this.password.isEmpty()) {
      throw new IllegalArgumentException("`password` is absent.");
    }

    byte[] randomBytes = new byte[6];
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64.encodeToString(randomBytes);
  }

  public ScramClient(final ScramMechanism scram,
                     final String username,
                     final byte[] saltedPassword,
                     final byte[] salt,
                     final int iteration,
                     final String authzId) {
    this.scram = scram;
    this.username = username == null ? "" : username;
    this.password = "";
    this.saltedPassword = saltedPassword == null
        ? new byte[0]
        : Arrays.copyOf(saltedPassword, saltedPassword.length);
    this.salt = salt == null ? new byte[0] : Arrays.copyOf(salt, salt.length);
    this.iteration = iteration;
    this.authzId = authzId == null ? "" : authzId;

    Objects.requireNonNull(scram);
    if (this.username.isEmpty()) {
      throw new IllegalArgumentException();
    }
    if (this.saltedPassword.length == 0) {
      throw new IllegalArgumentException();
    }
    if (this.salt.length == 0) {
      throw new IllegalArgumentException();
    }

    byte[] randomBytes = new byte[6];
    new SecureRandom().nextBytes(randomBytes);
    this.initialNounce = base64.encodeToString(randomBytes);
  }

  public int getIteration() {
    return iteration;
  }

  public byte[] getSaltedPassword() {
    return Arrays.copyOf(saltedPassword, saltedPassword.length);
  }

  public byte[] getSalt() {
    return Arrays.copyOf(salt, salt.length);
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
      case COMPLETED:
        return new byte[0];
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
  public boolean isCompleted() throws ScramException {
    if (error == null) {
      return state == State.COMPLETED;
    } else {
      throw error;
    }
  }

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
}