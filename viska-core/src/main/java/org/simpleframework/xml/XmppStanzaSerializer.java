package org.simpleframework.xml;

import java.io.InputStream;
import java.io.OutputStream;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

/**
 * @since 0.1
 */
public class XmppStanzaSerializer implements chat.viska.xmpp.StanzaSerializer {
  private static Serializer serializer = null;

  private static void initialeSerializer() {
    serializer = new Persister(new AnnotationStrategy());
  }

  @Override
  public <T> T read(Class<? extends T> type, InputStream input)
      throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    return serializer.read(type, input, false);
  }

  public <T> T read(Class<? extends T> type, InputNode input)
      throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    return serializer.read(type, input, false);
  }

  @Override
  public void write(Object source, OutputStream output) throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    serializer.write(source, output);
  }

  public void write(Object source, OutputNode output) throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    serializer.write(source, output);
  }
}