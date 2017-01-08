package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.ClassAttributeRemovingVisitor;
import org.simpleframework.xml.strategy.VisitorStrategy;
import org.simpleframework.xml.transform.RegistryMatcher;
import org.simpleframework.xml.transform.XmppJidTransform;
import org.simpleframework.xml.transform.XmppStanzaTypeTransform;

/**
 * @since 0.1
 */
@Root(name = "open")
@Namespace(reference = WebSocketStreamHeader.XMLNS)
public class WebSocketStreamHeader extends StreamHeader implements SimpleXmlSerializable {

  public static final String XMLNS = "urn:ietf:params:xml:ns:xmpp-framing";

  public static WebSocketStreamHeader readFromStartTag(Reader input)
      throws Exception {
    return SimpleXmlSerializable.readXml(WebSocketStreamHeader.class, input);
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

  @Override
  public void writeXml(Writer output) throws Exception {
    RegistryMatcher matcher = new RegistryMatcher();
    matcher.bind(Jid.class, new XmppJidTransform());
    matcher.bind(Stanza.Type.class, new XmppStanzaTypeTransform());
    Serializer serializer = new Persister(
        new AnnotationStrategy(new VisitorStrategy(
            new ClassAttributeRemovingVisitor()
        )),
        matcher
    );
    serializer.write(this, output);
  }

  @Override
  public void writeStartTag(Writer output) throws IOException {
    try {
      writeXml(output);
    } catch (Exception ex) {
      if (ex instanceof IOException) {
        throw (IOException) ex;
      } else {
        throw new RuntimeException(ex);
      }
    }
  }
}