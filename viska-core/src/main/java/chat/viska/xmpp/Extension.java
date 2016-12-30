package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.Set;

/**
 * XMPP Extension.
 * @since 0.1
 */
public interface Extension {

  Set<Class<? extends Extension>> getDependencies();

  boolean quickValidate(Stanza stanza);

  Session getSession();
}