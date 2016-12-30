package chat.viska.xmpp.jingle;

import chat.viska.xmpp.*;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class JingleExtension implements Extension {

  private Session xmppSession;

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    return new HashSet<>(0);
  }

  @Override
  public boolean quickValidate(Stanza stanza) {
    throw new RuntimeException();
  }

  @Override
  public Session getSession() {
    return xmppSession;
  }
}