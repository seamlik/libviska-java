package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.Set;
import java.util.UUID;

/**
 * @since 0.1
 */
public class Session {

  public enum State {

  }

  private final UUID id = UUID.randomUUID();
  private State state;
  private Set<? extends Extension> extensions;
  private String server;

  public Session(Jid jid) {

  }

  public Session(String server) {

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

  public void send(Stanza stanza) {

  }

  public State getState() {
    return state;
  }

  public void apply(Extension extension) {

  }

  public Set<? extends Extension> getExtensions() {
    throw new RuntimeException();
  }
}