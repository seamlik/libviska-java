package chat.viska.xmpp.jingle;

import java.util.UUID;

public class Session {

  private UUID innerId = UUID.randomUUID();
  private String sessionId;

  public enum State {

  }

  private State state;

  public State getState() {
    return state;
  }
}