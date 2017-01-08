package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;

/**
 * @since 0.1
 */
public class WebSocketSession extends Session {

  public WebSocketSession(String server, String username, String resource) {
    super(server, username);
  }

  public WebSocketSession(String server, String username) {
    this(server, username, null);
  }

  public WebSocketSession(String server) {
    this(server, null, null);
  }

  @Override
  public void connect() {

  }

  @Override
  public void disconnect() {

  }

  @Override
  public void send(Stanza stanza) {

  }

  @Override
  public State getState() {
    return null;
  }
}