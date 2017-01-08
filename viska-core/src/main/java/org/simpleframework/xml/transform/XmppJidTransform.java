package org.simpleframework.xml.transform;

import chat.viska.xmpp.Jid;

public class XmppJidTransform implements Transform<Jid> {
  @Override
  public Jid read(String value) throws Exception {
    return new Jid(Jid.parseJidParts(value));
  }

  @Override
  public String write(Jid value) throws Exception {
    return value.toString();
  }
}