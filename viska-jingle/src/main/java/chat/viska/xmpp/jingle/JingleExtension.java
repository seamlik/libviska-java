package chat.viska.xmpp.jingle;

import chat.viska.xmpp.BaseExtension;
import chat.viska.xmpp.DuplicatedExtensionsException;
import chat.viska.xmpp.Extension;
import chat.viska.xmpp.stanzas.JingleInfoQuery;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class JingleExtension extends Extension {

  public static final String ID = "jingle";

  private Set<? extends Session> jingleSessions;

  public JingleExtension(chat.viska.xmpp.Session session)
      throws DuplicatedExtensionsException {
    super(session);
  }

  @Override
  public Set<Class<? extends Extension>> getDependencies() {
    Set<Class<? extends Extension>> dependencies = new HashSet<>(1);
    dependencies.add(BaseExtension.class);
    return dependencies;
  }

  @Override
  public boolean quickMatch(Stanza stanza) {
    throw new RuntimeException();
  }

  @Override
  public Set<String> getFeatures() {
    Set<String> features = new HashSet<>();
    features.add(JingleInfoQuery.Jingle.XMLNS);
    return features;
  }

  @Override
  public String getExtensionId() {
    return ID;
  }
}