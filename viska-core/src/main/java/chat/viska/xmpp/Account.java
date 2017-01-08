package chat.viska.xmpp;

import java.util.Set;

/**
 * An XMPP account logged into an XMPP session.
 * @since 0.1
 */
public class Account extends AbstractAccount {

  private Session session;
  private Jid jid;

  public Account(Jid jid, Session session) {
    super(jid, session);
  }

  @Override
  public Set<String> getFeatures() {
    return null;
  }

  public void setPassword(String password) {

  }
}