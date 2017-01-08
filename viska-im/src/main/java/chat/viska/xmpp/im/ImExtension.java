package chat.viska.xmpp.im;

import chat.viska.xmpp.DuplicatedExtensionsException;
import chat.viska.xmpp.Extension;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class ImExtension extends Extension {

  public static final String ID = "im";

  public ImExtension(Session session) throws DuplicatedExtensionsException {
    super(session);
  }

  @Override
  public Set<String> getFeatures() {
    return null;
  }

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    return new HashSet<>(0);
  }

  @Override
  public boolean quickMatch(Stanza stanza) {
    return false;
  }

  @Override
  public String getExtensionId() {
    return ID;
  }
}