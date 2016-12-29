package org.simpleframework.xml.transform;

import chat.viska.xmpp.InvalidJidPartException;
import chat.viska.xmpp.InvalidJidSyntaxException;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.JidTooLongException;

public class JidTransform implements Transform<Jid> {

  public Jid read(String value) throws InvalidJidPartException,
                                       InvalidJidSyntaxException,
                                       JidTooLongException {
    return new Jid(Jid.parseJidParts(value));
  }

  public String write(Jid value) {
    return value.toString();
  }
}