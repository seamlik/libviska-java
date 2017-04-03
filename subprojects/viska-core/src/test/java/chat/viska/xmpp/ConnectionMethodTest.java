/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.xmpp;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link ConnectionMethod}.
 * @since 0.1
 */
public class ConnectionMethodTest {

  @Test
  public void queryHostMetaTest() throws Exception {
    Reader hostMetaJson = new InputStreamReader(
        getClass().getResourceAsStream("host-meta.json")
    );
    List<ConnectionMethod> jsonResult = ConnectionMethod.queryHostMetaJson(hostMetaJson);
    Assertions.assertEquals(
        "wss://im.example.org:443/ws",
        jsonResult.get(0).getUri().toString()
    );
    Assertions.assertEquals(
        "wss://im.example.org:443/xmpp",
        jsonResult.get(1).getUri().toString()
    );
    InputStream hostMetaXml = getClass().getResourceAsStream("host-meta.xml");
    Assertions.assertEquals(
        "wss://im.example.org:443/ws",
        ConnectionMethod.queryHostMetaXml(hostMetaXml).get(0).getUri().toString()
    );
  }
}