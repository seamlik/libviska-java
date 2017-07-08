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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ScramTest {

  @Test
  public void rawPassword() throws Exception {
    final Map<String, String> properties = new HashMap<>();
    properties.put("username", "admin");
    properties.put("password", "pencil");
    final ScramSha512Server server = new ScramSha512Server(
        new CredentialRetriever() {
          @Override
          public Object retrieve(String authnId, String mechanism, String key)
              throws AbortedException, IOException {
            return properties.get(key);
          }
        }
    );
    final ScramSha512Client client = new ScramSha512Client(
        "admin",
        "viska",
        new CredentialRetriever() {
          @Override
          public Object retrieve(String authnId, String mechanism, String key)
              throws IOException, AbortedException {
            return properties.get(key);
          }
        }
    );
    if (client.isClientFirst()) {
      final byte[] msg = client.respond();
      server.acceptResponse(msg);
    }
    while (true) {
      final byte[] challenge = server.challenge();
      client.acceptChallenge(challenge);
      if (client.isCompleted() && server.isCompleted()) {
        break;
      }
      final byte[] response = client.respond();
      server.acceptResponse(response);
      if (client.isCompleted() && server.isCompleted()) {
        break;
      }
    }
    Assertions.assertNull(client.getError());
    Assertions.assertNull(server.getError());
    Assertions.assertEquals(server.getAuthorizationId(), "viska");
  }
}