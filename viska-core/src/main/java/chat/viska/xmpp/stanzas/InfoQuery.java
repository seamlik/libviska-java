package chat.viska.xmpp.stanzas;

/**
 * {@code <iq/>} {@link Stanza}.
 * @since 0.1
 */
public interface InfoQuery extends Stanza {
  boolean needsAcknowledgement();
}