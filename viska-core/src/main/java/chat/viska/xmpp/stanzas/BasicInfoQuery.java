package chat.viska.xmpp.stanzas;


import chat.viska.xmpp.InvalidJidSyntaxException;
import chat.viska.xmpp.Jid;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 * Most basic {@code <iq/>} {@link Stanza}. This kind of {@code <iq/>} is mostly
 * used as an acknowledgement to another {@code <iq/>}.
 * @since 0.1
 */
@Root(name = "iq")
public final class BasicInfoQuery implements InfoQuery {

  @Attribute(required = false)
  private String id;

  @Attribute(required = false)
  private String type;

  @Attribute(name = "from", required = false)
  private String sender;

  @Attribute(name = "to", required = false)
  private String recipient;

  /**
   * Exists only for Simple XML.
   */
  private BasicInfoQuery() {}

  /**
   * Default constructor.
   * @param id See {@link InfoQuery#getId()}. This argument is mandatory.
   * @param type See {@link InfoQuery#getType()}.
   * @param sender See {@link InfoQuery#getSender()}.
   * @param recipient See {@link InfoQuery#getRecipient()}.
   * @throws IllegalArgumentException If {@code id} is {@code null} or empty.
   */
  public BasicInfoQuery(String id, Type type, Jid sender, Jid recipient) {
    if (id == null || id.isEmpty()) {
      throw new IllegalArgumentException();
    }
    if (type == null) {
      this.type = null;
    } else {
      this.type = type.toString();
    }
    this.id = id;
    this.sender = sender.toString();
    this.recipient = recipient.toString();
  }

  /**
   * Generates an {@link InfoQuery} serving as an acknowledgement of a given
   * {@code <iq/>}.
   * @param iq The given {@code <iq/>}.
   * @param sender See {@link InfoQuery#getSender()}.
   * @param recipient See {@link InfoQuery#getRecipient()}.
   * @return never {@code null}.
   */
  public static BasicInfoQuery acknowledgement(InfoQuery iq,
                                               Jid sender,
                                               Jid recipient) {
    return new BasicInfoQuery(iq.getId(), Type.RESULT, recipient, sender);
  }

  /**
   * Generates an {@link InfoQuery} serving as an acknowledgement of a given
   * {@code <iq/>}.
   * @param iq The given {@code <iq/>}.
   * @return never {@code null}.
   */
  public static BasicInfoQuery acknowledgement(InfoQuery iq) {
    return acknowledgement(iq, iq.getRecipient(), iq.getSender());
  }

  @Override
  public Type getType() {
    return Type.of(type);
  }

  @Override
  public Jid getSender() {
    return new Jid(Jid.parseJidParts(sender));
  }

  @Override
  public Jid getRecipient() {
    return new Jid(Jid.parseJidParts(recipient));
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean needsAcknowledgement() {
    return false;
  }
}