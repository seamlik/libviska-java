package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 *@since 0.1
 */
public class BaseExtension extends Extension {

  public static final String ID = "base";

  public BaseExtension(Session session) throws DuplicatedExtensionsException {
    super(session);
  }

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    return null;
  }

  @Override
  public boolean quickMatch(Stanza stanza) {
    return false;
  }

  @Override
  public Set<String> getFeatures() {
    return new HashSet<>(0);
  }

  @Override
  public String getExtensionId() {
    return ID;
  }
}