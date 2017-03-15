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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

/**
 * Tests of {@link NettyWebSocketSession}.
 */
public class NettyWebSocketSessionTest {

  @Test
  public void findWebSocketEndpointTest() throws Exception {
    Method jsonMethod = NettyWebSocketSession.class.getDeclaredMethod(
        "findWebSocketEndpoint", JsonElement.class
    );
    jsonMethod.setAccessible(true);
    JsonElement hostMetaJson = new JsonParser().parse(new InputStreamReader(
        getClass().getResourceAsStream("host-meta.json")
    ));
    Assertions.assertEquals(
        jsonMethod.invoke(null, hostMetaJson),
        "wss://im.example.org:443/ws"
    );
    Method xmlMethod = NettyWebSocketSession.class.getDeclaredMethod(
        "findWebSocketEndpoint", Document.class
    );
    xmlMethod.setAccessible(true);
    Document hostMetaXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
        getClass().getResourceAsStream("host-meta.xml")
    );
    hostMetaXml.normalizeDocument();
    Assertions.assertEquals(
        xmlMethod.invoke(null, hostMetaXml),
        "wss://im.example.org:443/ws"
    );
  }
}