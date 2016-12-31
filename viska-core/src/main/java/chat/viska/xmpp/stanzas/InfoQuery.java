package chat.viska.xmpp.stanzas;

/**
 * XMPP stanza for sending information or a request. It represents an
 * {@code <iq/>}.
 * @since 0.1
 */
public interface InfoQuery extends Stanza {

  /**
   * Determines whether this type of {@link InfoQuery} needs an acknowledgement.
   * If it does, a {@link chat.viska.xmpp.Session} should send an
   * acknowledgement {@link InfoQuery} back to the sender immediately after the
   * this {@link InfoQuery} is received. An acknowledgement can be easily
   * generated using
   * {@link BasicInfoQuery#acknowledgement(InfoQuery, chat.viska.xmpp.Jid, chat.viska.xmpp.Jid)}.
   * @see BasicInfoQuery#acknowledgement(InfoQuery, chat.viska.xmpp.Jid, chat.viska.xmpp.Jid)
   */
  boolean needsAcknowledgement();
}