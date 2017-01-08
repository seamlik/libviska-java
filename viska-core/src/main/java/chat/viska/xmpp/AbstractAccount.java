package chat.viska.xmpp;

import java.util.Set;

/**
 * @since 0.1
 */
public abstract class AbstractAccount implements SessionAware {
  private Session session;
  private Jid jid;

  public abstract Set<String> getFeatures();

  protected AbstractAccount(Jid jid, Session session) {
    this.jid = jid.toBareJid();
    this.session = session;
  }

  @Override
  public Session getSession() {
    return session;
  }

  public Jid getJid() {
    return jid;
  }
}