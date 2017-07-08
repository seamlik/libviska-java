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

public class ServerFactory {

  public static Server newServer(final String mechanism,
                                 final String name,
                                 final CredentialRetriever retriever) {
    switch (mechanism) {
      case "SCRAM-SHA-1":
        try {
          return new ScramSha1Server(retriever);
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      case "SCRAM-SHA-256":
        try {
          return new ScramSha256Server(retriever);
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      case "SCRAM-SHA-512":
        try {
          return new ScramSha512Server(retriever);
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      default:
        return null;
    }
  }
}