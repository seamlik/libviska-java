package chat.viska.xmpp;

import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * @since 0.1
 */
public abstract class Session {

  public enum State {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING
  }

  private final UUID id = UUID.randomUUID();
  private Set<Extension> extensions = new HashSet<>();
  private String server;
  private String username;

  public abstract void connect();

  public abstract void disconnect();

  public abstract void send(Stanza stanza);

  public abstract State getState();

  protected Session(String server, String username) {
    this.server = server;
    this.username = username;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof Session && ((Session)obj).id.equals(this.id);
  }

  public String getServer() {
    return server;
  }

  public String getUsername() {
    return username;
  }

  public void setResource(String resource) {

  }

  public void apply(Class<? extends Extension> type) {
    if (hasExtension(type)) {
      return;
    }
    Extension extension = null;
    try {
      extension = type.getConstructor(getClass()).newInstance(this);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Could not load extension" + type);
    }
    extensions.add(extension);
  }

  public Set<? extends Extension> getExtensions() {
    return new HashSet<>(extensions);
  }

  public boolean hasExtension(String id) {
    for (Extension it : extensions) {
      if (it.getExtensionId().equals(id)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasExtension(Class<? extends Extension> type) {
    for (Extension it : extensions) {
      if (type.isInstance(it)) {
        return true;
      }
    }
    return false;
  }
}