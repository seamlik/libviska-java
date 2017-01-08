package org.simpleframework.xml.transform;

import chat.viska.xmpp.stanzas.Stanza;

public class XmppStanzaTypeTransform implements Transform<Stanza.Type> {
  @Override
  public Stanza.Type read(String value) throws Exception {
    return Stanza.Type.of(value);
  }

  @Override
  public String write(Stanza.Type value) throws Exception {
    return value.toString();
  }
}