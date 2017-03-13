package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/**
 * @since 0.1
 */
public class TcpStreamHeader extends StreamHeader {

  public static final String XMLNS = "http://etherx.jabber.org/streams";
  public static final String CLIENT_XMLNS = "jabber:client";
  public static final String SERVER_CLIENT = "jabber:server";

  public static TcpStreamHeader readFromStartTag(Reader input) {
    String id;
    Jid sender;
    Jid recipient;
    String version;
    Type type;
    String lang;
    throw new RuntimeException();
  }

  public static void writeEndTag(Writer output) throws IOException {
    output.write("</stream:stream>");
  }

  public TcpStreamHeader(Type type,
                         Jid recipient,
                         Jid sender,
                         String id,
                         String version,
                         String lang) {
    super(type, recipient, sender, id, version, lang);
  }


  @Override
  public void writeStartTag(Writer output) throws IOException {
    output.append("<stream:stream xmlns=\"")
        .append((getType() == Type.CLIENT) ? CLIENT_XMLNS : SERVER_CLIENT)
        .append("\" xmlns:stream=\"")
        .append(XMLNS)
        .append("\"");
    if (getId() != null) {
      output.append(" id=\"").append(getId()).append("\"");
    }
    if (getSender() != null) {
      output.append(" from=\"").append(getSender().toString()).append("\"");
    }
    if (getRecipient() != null) {
      output.append(" to=\"").append(getRecipient().toString()).append("\"");
    }
    if (getVersion() != null) {
      output.append(" version=\"").append(getVersion()).append("\"");
    }
    if (getLang() != null) {
      output.append(" xml:lang=\"").append(getLang()).append("\"");
    }
    output.write("/>");
  }
}