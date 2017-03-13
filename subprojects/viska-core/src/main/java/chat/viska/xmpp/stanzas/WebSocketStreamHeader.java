package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * @since 0.1
 */
public class WebSocketStreamHeader extends StreamHeader {

  public static final String XMLNS = "urn:ietf:params:xml:ns:xmpp-framing";

  public static WebSocketStreamHeader readFromStartTag(Reader input) {
    throw new RuntimeException();
  }

  public static void writeEndTag(Writer output) throws IOException {
    output.append("<close xmlns=\"").append(XMLNS).append('\"').append("/>");
  }

  public WebSocketStreamHeader(Type type,
                               Jid recipient,
                               Jid sender,
                               String id,
                               String version,
                               String lang) {
    super(type, recipient, sender, id, version, lang);
  }

  public void writeStartTag(Writer output) throws IOException {

  }
}