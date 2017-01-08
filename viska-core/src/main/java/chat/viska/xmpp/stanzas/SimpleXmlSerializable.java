package chat.viska.xmpp.stanzas;


import chat.viska.xmpp.Jid;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.transform.RegistryMatcher;
import org.simpleframework.xml.transform.XmppJidTransform;
import org.simpleframework.xml.transform.XmppStanzaTypeTransform;

/**
 * @since 0.1
 */
public interface SimpleXmlSerializable {

  static <T extends SimpleXmlSerializable> T readXml(Class<? extends T> type,
                                                     Reader input)
      throws Exception {
    RegistryMatcher matcher = new RegistryMatcher();
    matcher.bind(Jid.class, new XmppJidTransform());
    matcher.bind(Stanza.Type.class, new XmppStanzaTypeTransform());
    Serializer serializer = new Persister(
        new AnnotationStrategy(),
        matcher
    );
    return serializer.read(type, input);
  }

  void writeXml(Writer output) throws Exception;
}