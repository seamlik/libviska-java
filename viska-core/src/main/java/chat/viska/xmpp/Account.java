package chat.viska.xmpp;

import java.util.Set;

/**
 * An XMPP account logged into an XMPP session.
 * @since 0.1
 */
public class Account implements SessionAware {
  private Jid jid;
  private Set<Contact> roster;
  private Session session;

  private Account() {}

  private void syncRoster() {

  }

  public Set<Contact> getRoster() {
    throw new RuntimeException();
  }

  public void addContact(Jid jid, String group) {

  }

  @Override
  public Session getSession() {
    return session;
  }
}