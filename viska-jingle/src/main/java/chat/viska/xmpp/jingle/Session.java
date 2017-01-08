package chat.viska.xmpp.jingle;

import chat.viska.xmpp.SessionAware;
import java.util.UUID;

/**
 * Jingle session.
 * @since 0.1
 */
public class Session implements SessionAware {

  public enum State {
    ACTIVE,
    ENDED,
    PENDING
  }

  private UUID innerId = UUID.randomUUID();
  private String sessionId;

  private State state;

  public State getState() {
    return state;
  }

  public chat.viska.xmpp.Session getSession() {
    throw new RuntimeException();
  }
}