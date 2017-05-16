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

import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Base64;

public class ScramClient implements Client {

  private final ScramMechanism scram;
  private final Base64 base64 = new Base64(false);
  private final String username;
  private final String password;
  private byte[] salt;
  private int iteration;

  public ScramClient(final String algorithm,
                     final String username,
                     final String password) throws NoSuchAlgorithmException {
    scram = new ScramMechanism(algorithm);
    this.username = username;
    this.password = password;
  }

  @Override
  public String getMechanismName() {
    return "SCRAM-" + scram.getHashAlgorithm();
  }

  @Override
  public byte[] respond(byte[] challenge) {
    return new byte[0];
  }

  @Override
  public byte[] respond() {
    return new byte[0];
  }

  @Override
  public boolean hasInitialResponse() {
    return false;
  }

  @Override
  public boolean isCompleted() throws AuthenticationException {
    return false;
  }
}