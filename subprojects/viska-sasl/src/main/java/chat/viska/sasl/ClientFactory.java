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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Factory to instantiate {@link Client}s from
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> Mechanism names.
 */
public class ClientFactory {

  private final List<String> mechanisms;

  public static Client newClient(String mechanism,
                                 String authnId,
                                 String authzId,
                                 CredentialRetriever retriever) {
    switch (mechanism) {
      case "SCRAM-SHA-1":
        try {
          return new ScramClient(
              new ScramMechanism("SHA-1"),
              authnId,
              authzId,
              retriever
          );
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      case "SCRAM-SHA-256":
        try {
          return new ScramClient(
              new ScramMechanism("SHA-256"),
              authnId,
              authzId,
              retriever
          );
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      case "SCRAM-SHA-512":
        try {
          return new ScramClient(
              new ScramMechanism("SHA-512"),
              authnId,
              authzId,
              retriever
          );
        } catch (NoSuchAlgorithmException ex) {
          return null;
        }
      default:
        return null;
    }
  }

  public ClientFactory(final List<String> mechanisms) {
    this.mechanisms = new ArrayList<>(mechanisms);
  }

  public Client newClient(List<String> mechanisms,
                          String authnId,
                          String authzId,
                          CredentialRetriever retriever) {
    for (String mech : this.mechanisms) {
      if (mechanisms.contains(mech)) {
        return newClient(mech, authnId, authzId, retriever);
      }
    }
    return null;
  }

  public List<String> getPreferredMechanisms() {
    return new ArrayList<>(mechanisms);
  }
}