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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;

public class ScramMechanism {

  private final MessageDigest hash;
  private final Mac hmac;

  static final String GS2_HEADER = "n,,";

  public ScramMechanism(final MessageDigest hash,
                        final Mac hmac) {
    Objects.requireNonNull(hash);
    Objects.requireNonNull(hmac);
    this.hash = hash;
    this.hmac = hmac;
  }

  static String getClientFirstMessageBare(final String username,
                                          final String nounce) {
    return String.format(
        "n=%1s,r=%2s",
        username.replace("=", "=3D").replace(",", "=2C"),
        nounce
    );
  }


  static String getServerFirstMessage(final String nounce,
                                      final byte[] salt,
                                      final int iteration) {
    return String.format(
        "r=%1s,s=%2s,i=%3s",
        nounce,
        Base64.encodeBase64String(salt),
        iteration
    );
  }

  static String getClientFinalMessageWithoutProof(String nounce) {
    return String.format(
        "c=%1s,r=%2s",
        Base64.encodeBase64String(GS2_HEADER.getBytes(StandardCharsets.UTF_8)),
        nounce
    );
  }

  static String getAuthMessage(final String initialNounce,
                               final String fullNounce,
                               final String username,
                               final byte[] salt,
                               final int iteration) {
    return String.format(
        "%1s,%2s,%3s",
        getClientFirstMessageBare(username, initialNounce),
        getServerFirstMessage(fullNounce, salt, iteration),
        getClientFinalMessageWithoutProof(fullNounce)
    );
  }

  static Map<String, String> convertMessageToMap(final String msg)
      throws ScramException {
    if (!msg.startsWith(GS2_HEADER)) {
      throw new ScramException("invalid-syntax");
    }
    Map<String, String> params = new HashMap<>();
    for (String it : msg.substring(GS2_HEADER.length()).split(",")) {
      String[] pair = it.split("=");
      if (pair.length > 2 || pair[0].isEmpty()) {
        throw new ScramException("invalid-syntax");
      }
      if (params.containsKey(pair[0])) {
        throw new ScramException("duplicated-attributes");
      }
      if (pair.length == 1) {
        params.put(pair[0], "");
      } else {
        params.put(pair[0], pair[1]);
      }
    }
    return params;
  }

  byte[] getSaltedPassword(final String password,
                           final byte[] salt,
                           int iteration) throws InvalidKeyException {
    return hi(password.getBytes(StandardCharsets.UTF_8), salt, iteration);
  }

  byte[] getClientKey(final byte[] saltedPassword)
      throws InvalidKeyException {
    hmac.init(new SecretKeySpec(saltedPassword, hmac.getAlgorithm()));
    return hmac.doFinal("Client Key".getBytes(StandardCharsets.UTF_8));
  }

  byte[] getStoredKey(final byte[] clientKey) {
    return hash.digest(clientKey);
  }

  byte[] getServerKey(final byte[] saltedPassword)
      throws InvalidKeyException {
    hmac.init(new SecretKeySpec(saltedPassword, hmac.getAlgorithm()));
    return hmac.doFinal("Server Key".getBytes(StandardCharsets.UTF_8));
  }

  byte[] getClientSignature(final byte[] storedKey, final String authMessage)
      throws InvalidKeyException {
    hmac.init(new SecretKeySpec(storedKey, hmac.getAlgorithm()));
    return hmac.doFinal(authMessage.getBytes(StandardCharsets.UTF_8));
  }

  byte[] getServerSignature(final byte[] serverKey, final String authMessage)
      throws InvalidKeyException {
    hmac.init(new SecretKeySpec(serverKey, hmac.getAlgorithm()));
    return hmac.doFinal(authMessage.getBytes(StandardCharsets.UTF_8));
  }

  public String getAlgorithm() {
    return hash.getAlgorithm();
  }

  public byte[] hi(byte[] data, byte[] salt, int iteration) throws InvalidKeyException {
    if (iteration < 1) {
      throw new IllegalArgumentException();
    }
    hmac.init(new SecretKeySpec(data, hmac.getAlgorithm()));
    byte[] raw = new byte[salt.length + 4];
    raw[raw.length - 1] = 1;
    byte[] rawNext = hmac.doFinal(raw);
    byte[] result = Bytes.xor(raw, rawNext);
    for (int it = 2; it <= iteration; ++it) {
      raw = rawNext;
      rawNext = hmac.doFinal(raw);
      result = Bytes.xor(result, rawNext);
    }
    return result;
  }
}