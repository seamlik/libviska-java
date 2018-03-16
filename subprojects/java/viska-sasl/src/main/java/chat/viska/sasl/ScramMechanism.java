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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents the hash algorithm used by a {@literal SCRAM-*} mechanism.
 */
public class ScramMechanism {

  /**
   * Key to a {@link Integer} representing the iteration count.
   */
  public static final String KEY_ITERATION = "iteration";
  /**
   * Key to a {@link String} representing a raw password.
   */
  public static final String KEY_PASSWORD = "password";
  /**
   * Key to a {@code byte[]} representing a salt.
   */
  public static final String KEY_SALT = "salt";
  /**
   * Key to a {@code byte[]} representing a salted password.
   */
  public static final String KEY_SALTED_PASSWORD = "salted-password";

  private final MessageDigest hash;
  private final Mac hmac;
  private final String algorithm;
  private final SecretKeyFactory keyFactory;
  private final Base64.Decoder base64Decoder = Base64.getDecoder();
  private final Base64.Encoder base64Encoder = Base64.getEncoder();

  static String getClientFirstMessageBare(final String username,
                                          final String nounce) {
    return String.format(
        "n=%1s,r=%2s",
        username.replace("=", "=3D").replace(",", "=2C"),
        nounce
    );
  }

  static Map<String, String> convertMessageToMap(final String msg, final boolean hasGs2Header) {
    final String msgBare;
    final String gs2Header;
    if (hasGs2Header) {
      final int gs2HeaderEndIndex = msg.indexOf(',', msg.indexOf(',') + 1) + 1;
      msgBare = msg.substring(gs2HeaderEndIndex);
      gs2Header = msg.substring(0, gs2HeaderEndIndex);
      if (gs2Header.isEmpty()) {
        throw new IllegalArgumentException("Invalid syntax.");
      }
    } else {
      msgBare = msg;
      gs2Header = "";
    }
    Map<String, String> params = new HashMap<>();
    for (String it : msgBare.split(",")) {
      final int equalSignIndex = it.indexOf('=');
      if (equalSignIndex < 1) {
        throw new IllegalArgumentException("Invalid syntax.");
      }
      final String key = it.substring(0, equalSignIndex);
      final String value = it.substring(equalSignIndex + 1);
      if (params.containsKey(key)) {
        throw new IllegalArgumentException("Duplicated attributes.");
      }
      params.put(key, value);
    }
    if (!gs2Header.isEmpty()) {
      params.put("gs2-header", gs2Header);
      String[] gs2HeaderParts = gs2Header.split(",");
      params.put("gs2-cbind-flag", gs2HeaderParts[0]);
      if (gs2HeaderParts.length == 2) {
        params.put("a", gs2HeaderParts[1].substring(2));
      }
    }
    return params;
  }

  String getAuthMessage(final String initialNounce,
                        final String fullNounce,
                        final String username,
                        final byte[] salt,
                        final int iteration,
                        final String gs2Header) {
    return String.format(
        "%1s,%2s,%3s",
        getClientFirstMessageBare(username, initialNounce),
        getServerFirstMessage(fullNounce, salt, iteration),
        getClientFinalMessageWithoutProof(fullNounce, gs2Header)
    );
  }

  private String getServerFirstMessage(final String nounce,
                                       final byte[] salt,
                                       final int iteration) {
    return String.format(
        "r=%1s,s=%2s,i=%3s",
        nounce,
        base64Encoder.encodeToString(salt),
        iteration
    );
  }

  String getClientFinalMessageWithoutProof(final String nounce, final String gs2Header) {
    return String.format(
        "c=%1s,r=%2s",
        base64Encoder.encodeToString(gs2Header.getBytes(StandardCharsets.UTF_8)),
        nounce
    );
  }

  byte[] getSaltedPassword(final String password,
                           final byte[] salt,
                           int iteration) throws InvalidKeySpecException {
    return hi(password, salt, iteration);
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

  public ScramMechanism(final MessageDigest hash,
                        final Mac hmac,
                        final SecretKeyFactory keyFactory,
                        @Nullable final String algorithm) {
    this.hash = hash;
    this.hmac = hmac;
    this.keyFactory = keyFactory;
    this.algorithm = algorithm == null ? "" : algorithm;
  }

  public ScramMechanism(final String algorithm) throws NoSuchAlgorithmException {
    final String hmacAlgorithm = algorithm.startsWith("SHA3")
        ? "Hmac" + algorithm
        : "Hmac" + algorithm.replace("-", "");
    this.algorithm = algorithm;
    this.hash = MessageDigest.getInstance(algorithm);
    this.hmac = Mac.getInstance(hmacAlgorithm);
    this.keyFactory = SecretKeyFactory.getInstance(
        "PBKDF2With" + hmacAlgorithm
    );
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public byte[] hi(final String password,
                   final byte[] salt,
                   final int iteration) throws InvalidKeySpecException {
    return keyFactory
        .generateSecret(
            new PBEKeySpec(password.toCharArray(),
                salt,
                iteration,
                hmac.getMacLength() * 8
            ))
        .getEncoded();
  }
}