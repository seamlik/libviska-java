package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;

/**
 * @since 0.1
 */
public class TcpSession extends Session {

  public TcpSession(String server, String username, String resource) {
    super(server, username);
  }

  public TcpSession(String server, String username) {
    this(server, username, null);
  }

  public TcpSession(String server) {
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