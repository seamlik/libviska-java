package org.simpleframework.xml.convert;

import chat.viska.xmpp.stanzas.JingleInfoQuery;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.ClassAttributeRemovingVisitor;
import org.simpleframework.xml.strategy.VisitorStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class XmppJingleDescriptionConverter
    implements Converter<JingleInfoQuery.Jingle.Content.Description> {

  private static Serializer serializer = new Persister(new AnnotationStrategy(
      new VisitorStrategy(new ClassAttributeRemovingVisitor()))
  );

  @Override
  public JingleInfoQuery.Jingle.Content.Description read(InputNode input)
      throws Exception {
    switch (input.getReference()) {
      case JingleInfoQuery.Jingle.Content.RtpDescription.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.RtpDescription.class,
          input,
          false
        );
      case JingleInfoQuery.Jingle.Content.FileTransferDescription.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.FileTransferDescription.class,
          input,
          false
        );
      default:
        return null;
    }
  }

  @Override
  public void write(OutputNode output,
                    JingleInfoQuery.Jingle.Content.Description value)
      throws Exception {
    serializer.write(value, output);
    output.getAttributes().remove("class");
  }
}