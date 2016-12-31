package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.Set;

/**
 *@since 0.1
 */
public class BaseExtension implements Extension {

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    return null;
  }

  @Override
  public boolean quickValidate(Stanza stanza) {
    return false;
  }

  @Override
  public Session getSession() {
    return null;
  }
}