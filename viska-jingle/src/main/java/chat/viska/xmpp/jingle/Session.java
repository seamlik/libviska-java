package chat.viska.xmpp.jingle;

import chat.viska.xmpp.SessionAware;
import java.util.UUID;

/**
 * Jingle session.
 * @since 0.1
 */
public class Session implements SessionAware {

  private UUID innerId = UUID.randomUUID();
  private String sessionId;

  public enum State {

  }

  private State state;

  public State getState() {
    return state;
  }

  public chat.viska.xmpp.Session getSession() {
    throw new RuntimeException();
  }
}