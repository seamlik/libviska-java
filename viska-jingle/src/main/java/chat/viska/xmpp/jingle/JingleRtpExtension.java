package chat.viska.xmpp.jingle;

import chat.viska.xmpp.Extension;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class JingleRtpExtension implements Extension {

  private chat.viska.xmpp.Session xmppSession;
  private JingleExtension jingleContext;

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    Set<Class<? extends Extension>> dependencies = new HashSet<>();
    dependencies.add(JingleExtension.class);
    return dependencies;
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
