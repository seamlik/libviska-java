package chat.viska.xmpp;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests of {@link TyrusWebSocketSession}.
 */
public class TyrusWebSocketSessionTest {

  @Test
  public void findWebSocketEndpointTest() throws Exception {
    Method jsonMethod = TyrusWebSocketSession.class.getDeclaredMethod(
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
    Method xmlMethod = TyrusWebSocketSession.class.getDeclaredMethod(
        "findWebSocketEndpoint", Document.class
    );
    xmlMethod.setAccessible(true);
    Document hostMetaXml = new SAXReader().read(
        getClass().getResourceAsStream("host-meta.xml")
    );
    Assertions.assertEquals(
        xmlMethod.invoke(null, hostMetaXml),
        "wss://im.example.org:443/ws"
    );
  }
}