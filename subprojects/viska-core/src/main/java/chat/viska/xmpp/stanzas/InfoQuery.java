package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;

/**
 * XMPP stanza for sending information or a request. It represents an
 * {@code <iq/>}.
 * @since 0.1
 */
public abstract class InfoQuery extends Stanza {

  /**
   * Default constructor.
   * @param id See {@link Stanza#getId()}. This argument is mandatory.
   * @param type See {@link Stanza#getType()}. This argument is mandatory.
   * @param recipient See {@link Stanza#getRecipient()}.
   * @param sender See {@link Stanza#getSender()}.
   * @throws NullPointerException If {@code id} or {@code type} is {@code null}.
   *         The message of the exception is the XPath to the missing attribute.
   */
  protected InfoQuery(String id, Type type, Jid recipient, Jid sender) {
    super(id, type, sender, recipient);
    if (id == null) {
      throw new NullPointerException("/iq[@id]");
    }
    if (type == null) {
      throw new NullPointerException("/iq[@type]");
    }
  }

  /**
   * Determines whether this type of {@link InfoQuery} needs an acknowledgement.
   * If it does, a {@link chat.viska.xmpp.Session} should send an
   * acknowledgement {@link InfoQuery} back to the sender immediately after the
   * this {@link InfoQuery} is received. An acknowledgement can be easily
   * generated using
   * {@link BasicInfoQuery#acknowledgement(InfoQuery, chat.viska.xmpp.Jid, chat.viska.xmpp.Jid)}.
   * @see BasicInfoQuery#acknowledgement(InfoQuery, chat.viska.xmpp.Jid, chat.viska.xmpp.Jid)
   */
  public abstract boolean needsAcknowledgement();
}