package org.simpleframework.xml.convert;

import chat.viska.xmpp.stanzas.JingleInfoQuery;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.ClassAttributeRemovingVisitor;
import org.simpleframework.xml.strategy.VisitorStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class XmppJingleTransportConverter
    implements Converter<JingleInfoQuery.Jingle.Content.Transport> {

  private static Serializer serializer = new Persister(new AnnotationStrategy(
      new VisitorStrategy(new ClassAttributeRemovingVisitor()))
  );

  @Override
  public JingleInfoQuery.Jingle.Content.Transport read(InputNode input)
      throws Exception {
    switch (input.getReference()) {
      case JingleInfoQuery.Jingle.Content.IceUdpTransport.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.IceUdpTransport.class,
          input,
          false
        );
      case JingleInfoQuery.Jingle.Content.RawUdpTransport.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.RawUdpTransport.class,
          input,
          false
        );
      default:
        return null;
    }
  }

  @Override
  public void write(OutputNode output,
                    JingleInfoQuery.Jingle.Content.Transport value)
      throws Exception {
    serializer.write(value, output);
  }
}