package chat.viska.xmpp.stanzas;


import chat.viska.xmpp.Jid;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
@Root(name = "iq")
public class BasicInfoQuery implements InfoQuery {

  @Attribute
  private String id;

  @Attribute
  private String type;

  @Attribute(name = "from")
  private Jid sender;

  @Attribute(name = "to")
  private Jid recipient;

  /**
   * Exists only for Simple XML.
   */
  private BasicInfoQuery(@Attribute(name = "id") String id,
                         @Attribute(name = "type") String type,
                         @Attribute(name = "from") Jid sender,
                         @Attribute(name = "to") Jid recipient) {
    Enum.valueOf(Type.class, type.toUpperCase());
    this.id = id;
    this.type = type;
    this.sender = sender;
    this.recipient = recipient;
  }

  /**
   * Constructs a {@link BasicInfoQuery} with every field specified.
   * @param id The {@code id} of the iq stanza.
   * @param type The {@code type} attribute of the iq stanza.
   * @param sender The {@code from} attribute of the iq stanza.
   * @param recipient The {@code to} attribute of the iq stanza.
   */
  public BasicInfoQuery(String id, Type type, Jid sender, Jid recipient) {
    this.id = id;
    this.type = type.name();
    this.sender = sender;
    this.recipient = recipient;
  }

  /**
   * Generates an {@link InfoQuery} representing an acknowledgement of a given
   * {@code <iq/>} stanza.
   * @param iq The given {@code <iq/>} stanza.
   * @param sender The {@code from} attribute of the acknowledgement.
   * @param recipient The {@code to} attribute of the acknowledgement.
   * @return never {@code null}.
   */
  public static BasicInfoQuery acknowledge(InfoQuery iq,
                                           Jid sender,
                                           Jid recipient) {
    return new BasicInfoQuery(
      iq.getId(),
      Type.RESULT.toString(),
      recipient,
      sender
    );
  }

  @Override
  public Stanza.Type getType() {
    return Enum.valueOf(Type.class, type.toUpperCase());
  }

  @Override
  public Jid getSender() {
    return sender;
  }

  @Override
  public Jid getRecipient() {
    return recipient;
  }

  @Override
  public String getId() {
    return id;
  }
}