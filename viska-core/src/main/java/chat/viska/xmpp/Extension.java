package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;

/**
 * XMPP Extension.
 * @since 0.1
 */
public interface Extension {

  Class<? extends Stanza>[] getDefinedStanzaTypes();

  Extension[] getDependencies();
}