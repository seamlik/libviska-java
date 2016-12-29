package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.UUID;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class Session {

  public enum State {

  }

  private final UUID id = UUID.randomUUID();
  private Account account;
  private State state;

  public Session(Jid jid) {

  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Session)) {
      return false;
    }
    return ((Session)obj).id.equals(this.id);
  }

  public void connect() {

  }

  public void disconnect() {

  }

  public void login() {

  }

  public void logout() {

  }

  public void send(Stanza stanza) {

  }

  public State getState() {
    return state;
  }

  public void setResource(String resource) {

  }

  public void apply(Extension extension) {

  }
}