package chat.viska.xmpp;

import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class RemoteAccount extends AbstractAccount {

  private Set<String> resources = new HashSet<>();

  public RemoteAccount(Jid jid, Session session) {
    super(jid, session);
  }

  @Override
  public Set<String> getFeatures() {
    return null;
  }

  @Override
  public Jid getJid() {
    return super.getJid();
  }
}