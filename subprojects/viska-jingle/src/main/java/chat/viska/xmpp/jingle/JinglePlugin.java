package chat.viska.xmpp.jingle;

import chat.viska.xmpp.BasePlugin;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class JinglePlugin extends Plugin {

  private Set<? extends Session> jingleSessions;


  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    Set<Class<? extends Plugin>> dependencies = new HashSet<>(1);
    dependencies.add(BasePlugin.class);
    return dependencies;
  }

  @Override
  public boolean quickMatch(Stanza stanza) {
    throw new RuntimeException();
  }

  @Override
  public Set<String> getFeatures() {
    Set<String> features = new HashSet<>();
    //features.add(JingleInfoQuery.Jingle.XMLNS);
    return features;
  }
}