package chat.viska.xmpp.im;

import chat.viska.xmpp.AbstractAccount;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.SessionAware;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class ChatRoom implements SessionAware {

  private Set<AbstractAccount> participants = new HashSet<>();

  @Override
  public Session getSession() {
    return null;
  }
}