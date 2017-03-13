package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;

/**
 * Most basic {@code <iq/>} {@link Stanza}. This kind of {@code <iq/>} is mostly
 * used as an acknowledgement to another {@code <iq/>}.
 * @since 0.1
 */
public class BasicInfoQuery extends InfoQuery {

  /**
   * Default constructor.
   * @param id See {@link Stanza#getId()}. This argument is mandatory.
   * @param type See {@link Stanza#getType()}. This argument is mandatory.
   * @param recipient See {@link Stanza#getRecipient()}.
   * @param sender See {@link Stanza#getSender()}.
   * @throws IllegalArgumentException If {@code id} is {@code null} or empty.
   */
  public BasicInfoQuery(String id, Type type, Jid recipient, Jid sender) {

    super(id, type, recipient, sender);
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
    return new BasicInfoQuery(iq.getId(), Type.RESULT, sender, recipient);
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
  public boolean needsAcknowledgement() {
    return false;
  }
}