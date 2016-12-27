package org.simpleframework.xml;

import chat.viska.xmpp.Jid;
import java.io.InputStream;
import java.io.OutputStream;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.transform.JidTransform;
import org.simpleframework.xml.transform.RegistryMatcher;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class XmppStanzaSerializer implements chat.viska.xmpp.StanzaSerializer {
  private static Serializer serializer = null;

  private static void initialeSerializer() {
    RegistryMatcher matcher = new RegistryMatcher();
    matcher.bind(Jid.class, JidTransform.class);
    serializer = new Persister(matcher);
  }

  @Override
  public <T> T read(Class<? extends T> type, InputStream input)
      throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    return serializer.read(type, input);
  }

  public <T> T read(Class<? extends T> type, InputNode input) throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    return serializer.read(type, input);
  }

  @Override
  public void write(Object source, OutputStream output) throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    serializer.write(source, output);
  }

  public <T> void write(T source, OutputNode output) throws Exception {
    if (serializer == null) {
      initialeSerializer();
    }
    serializer.write(source, output);
  }
}