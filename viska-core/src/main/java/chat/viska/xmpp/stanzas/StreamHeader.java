package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * @since 0.1
 */
public abstract class StreamHeader {

  public enum Type {
    CLIENT,
    SERVER
  }

  private String id;
  private Jid sender;
  private Jid recipient;
  private String version;
  private Type type;
  private String lang;

  public abstract void writeStartTag(Writer output) throws IOException;

  public StreamHeader(Type type,
                      Jid recipient,
                      Jid sender,
                      String id,
                      String version,
                      String lang) {
    if (type == null) {
      throw new NullPointerException(
          "Type must be specified when constructing a stream!"
      );
    }
    this.id = id;
    this.sender = sender;
    this.recipient = recipient;
    this.version = version;
    this.type = type;
    this.lang = lang;
  }

  public String getId() {
    return id;
  }

  public Jid getSender() {
    return sender;
  }

  public Jid getRecipient() {
    return recipient;
  }

  public String getVersion() {
    return version;
  }

  public Type getType() {
    return type;
  }

  public String getLang() {
    return lang;
  }
}
