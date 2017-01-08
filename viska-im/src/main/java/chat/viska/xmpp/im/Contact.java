package chat.viska.xmpp.im;

import chat.viska.xmpp.Jid;
import chat.viska.xmpp.RemoteAccount;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.SessionAware;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class Contact implements SessionAware {

  private RemoteAccount account;
  private Set<String> groups = new HashSet<>();
  private Jid jid;

  public Set<? extends String> getGroups() {
    return new HashSet<>(groups);
  }

  public void addGroup(String group) {

  }

  public void removeGroup(String group) {

  }

  @Override
  public Session getSession() {
    return account.getSession();
  }
}