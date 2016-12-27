package org.simpleframework.xml.convert;

import chat.viska.xmpp.stanzas.JingleInfoQuery;
import org.simpleframework.xml.XmppStanzaSerializer;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class JingleDescriptionConverter
    implements Converter<JingleInfoQuery.Jingle.Content.Description> {

  @Override
  public JingleInfoQuery.Jingle.Content.Description read(InputNode input)
      throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    switch (input.getReference()) {
      case JingleInfoQuery.Jingle.Content.RtpDescription.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.RtpDescription.class,
          input
        );
      case JingleInfoQuery.Jingle.Content.FileTransferDescription.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.FileTransferDescription.class,
          input
        );
      default:
        return null;
    }
  }

  @Override
  public void write(OutputNode output,
                    JingleInfoQuery.Jingle.Content.Description value)
      throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    if (value instanceof JingleInfoQuery.Jingle.Content.RtpDescription) {
      serializer.write(
          (JingleInfoQuery.Jingle.Content.RtpDescription)value,
          output
      );
    } else if (value instanceof JingleInfoQuery.Jingle.Content.FileTransferDescription) {
      serializer.write(
          (JingleInfoQuery.Jingle.Content.FileTransferDescription)value,
          output
      );
    }
  }
}