package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.io.Reader;
import java.io.Writer;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.ClassAttributeRemovingVisitor;
import org.simpleframework.xml.strategy.VisitorStrategy;

/**
 * @since 0.1
 */
public class SimpleXmlSerializer implements chat.viska.xmpp.StanzaSerializer {
  private static Serializer serializer = null;

  private static void initiateSerializer() {
    serializer = new Persister(new AnnotationStrategy(new VisitorStrategy(
        new ClassAttributeRemovingVisitor())
    ));
  }

  @Override
  public <T extends Stanza> T read(Class<? extends T> type, Reader input)
      throws Exception {
    if (serializer == null) {
      initiateSerializer();
    }
    return serializer.read(type, input, false);
  }

  @Override
  public void write(Object source, Writer output) throws Exception {
    if (serializer == null) {
      initiateSerializer();
    }
    serializer.write(source, output);
  }
}