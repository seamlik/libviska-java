package org.simpleframework.xml.convert;

import chat.viska.xmpp.stanzas.JingleInfoQuery;
import org.simpleframework.xml.XmppStanzaSerializer;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class XmppJingleTransportConverter
    implements Converter<JingleInfoQuery.Jingle.Content.Transport> {

  @Override
  public JingleInfoQuery.Jingle.Content.Transport read(InputNode input)
      throws Exception {
    XmppStanzaSerializer serializer = new XmppStanzaSerializer();
    switch (input.getReference()) {
      case JingleInfoQuery.Jingle.Content.IceUdpTransport.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.IceUdpTransport.class,
          input
        );
      case JingleInfoQuery.Jingle.Content.RawUdpTransport.XMLNS:
        return serializer.read(
          JingleInfoQuery.Jingle.Content.RawUdpTransport.class,
          input
        );
      default:
        return null;
    }
  }

  @Override
  public void write(OutputNode output,
                    JingleInfoQuery.Jingle.Content.Transport value)
      throws Exception {
    new XmppStanzaSerializer().write(value, output);
  }
}