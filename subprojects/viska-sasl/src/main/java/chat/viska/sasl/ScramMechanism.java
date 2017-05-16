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

import com.ibm.icu.text.StringPrep;
import com.ibm.icu.text.StringPrepParseException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

public class ScramMechanism {

  private final MessageDigest hash;
  private final Mac hmac;
  private final SecretKeyFactory keyFactory;
  private final StringPrep prep = StringPrep.getInstance(StringPrep.RFC4013_SASLPREP);

  public ScramMechanism(String algorithm) throws NoSuchAlgorithmException {
    hash = MessageDigest.getInstance(algorithm);
    hmac = Mac.getInstance(algorithm);
    keyFactory = SecretKeyFactory.getInstance(algorithm);
  }

  public byte[] getSaltedPassword(final String rawPassword,
                                  final byte[] salt,
                                  int iteration)
      throws StringPrepParseException {
    final byte[] password = prep
        .prepare(rawPassword, StringPrep.DEFAULT)
        .getBytes(StandardCharsets.UTF_8);
    try {
      hmac.init(keyFactory.generateSecret(
          new SecretKeySpec(password, getHashAlgorithm())
      ));
    } catch (InvalidKeySpecException | InvalidKeyException ex) {
      throw new IllegalArgumentException(ex);
    }
    byte[] raw = new byte[password.length + 4];
    raw[raw.length - 1] = 1;
    byte[] result = hmac.doFinal(raw);
    for (int it = 2; it <= iteration; ++it) {
      raw = result;
      result = hmac.doFinal(raw);
    }
    return raw;
  }

  public byte[] getClientKey(final byte[] saltedPassword) {
    try {
      hmac.init(keyFactory.generateSecret(
          new SecretKeySpec(saltedPassword, getHashAlgorithm())
      ));
    } catch (InvalidKeySpecException | InvalidKeyException ex) {
      throw new IllegalArgumentException(ex);
    }
    return hmac.doFinal("Client Key".getBytes(StandardCharsets.UTF_8));
  }

  public byte[] getStoredKey(final byte[] clientKey) {
    return hash.digest(clientKey);
  }

  public byte[] getServerKey(final byte[] saltedPassword) {
    try {
      hmac.init(keyFactory.generateSecret(
          new SecretKeySpec(saltedPassword, getHashAlgorithm())
      ));
    } catch (InvalidKeySpecException | InvalidKeyException ex) {
      throw new IllegalArgumentException(ex);
    }
    return hmac.doFinal("Server Key".getBytes(StandardCharsets.UTF_8));
  }

  public String getHashAlgorithm() {
    return hash.getAlgorithm();
  }
}