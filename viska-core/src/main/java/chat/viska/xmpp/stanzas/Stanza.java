package chat.viska.xmpp.stanzas;

import chat.viska.xmpp.Jid;
import java.util.Arrays;
import java.util.List;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public interface Stanza {

  /**
   * The type of a {@link Stanza}.
   * <p>
   *   Different stanzas support different sets of types. Note that only
   *   {@link Type#ERROR} is the common type of all stanzas.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-5.2.2">Type
   *      attribute of a message stanza</a>
   * @see <a href="https://tools.ietf.org/html/rfc6121#section-4.7.1">Type
   *      attribute of a presence stanza</a>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.2.3">Type
   *      attribute of an iq stanza</a>
   */
  enum Type {

    /**
     * Sending the {@link Message} to a ont-to-one chat session.
     */
    CHAT(Message.class),

    /**
     * Indicating an error has occurred.
     */
    ERROR(InfoQuery.class, Message.class, Presence.class),

    /**
     * Requesting information, inquires about what data is needed in order to
     * complete further operations.
     */
    GET(InfoQuery.class),

    /**
     * Sending the {@link Message} to a multi-user chat session.
     */
    GROUPCHAT(Message.class),

    /**
     * Sending an alert, a notification, or other transient information to which
     * no reply is expected.
     */
    HEADLINE(Message.class),

    /**
     * Sending the {@link Message} to a one-to-one or multi-user chat session.
     */
    NORMAL(Message.class),

    /**
     * Requesting for an entity's current presence.
     */
    PROBE(Presence.class),

    /**
     * Representing a response to a successful get or set request.
     */
    RESULT(InfoQuery.class),

    /**
     * Providing data that is needed for an operation to be completed, setting
     * new values, replacing existing values, etc..
     */
    SET(InfoQuery.class),

    /**
     * Wishing to subscribe to the recipient's presence.
     */
    SUBSCRIBE(Presence.class),

    /**
     * Allowing the recipient to receive the presence.
     */
    SUBSCRIBED(Presence.class),

    /**
     * Indicating the sender is no longer available for communication.
     */
    UNAVAILABLE(Presence.class),

    /**
     * Unsubscribing from the recipient's presence.
     */
    UNSUBSCRIBE(Presence.class),

    /**
     * Denying the subscription request or indicating a previously granted
     * subscription has been canceled.
     */
    UNSUBSCRIBED(Presence.class);

    private List<Class<?>> applicableStanzasTypes;

    Type(Class<?>... applicableStanzasTypes) {
      this.applicableStanzasTypes = Arrays.asList(applicableStanzasTypes);
    }

    public boolean isInfoQueryType() {
      return applicableStanzasTypes.contains(InfoQuery.class);
    }

    public boolean isMessageType() {
      return applicableStanzasTypes.contains(Message.class);
    }

    public boolean isPresenceType() {
      return applicableStanzasTypes.contains(Presence.class);
    }
  }

  /**
   * Returns the recipient of this stanza.
   * <p>
   *   This method must return {@code null} if the stanza was sent to the server
   *   for processing commands.
   * </p>
   * <p>
   *   This property corresponds to the "to" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.1">RFC 6120</a>
   *
   */
  Jid getRecipient();

  /**
   * Returns the sender of this stanza.
   * <p>
   *   This property corresponds to the "from" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.2">RFC 6120</a>
   */
  Jid getSender();

  /**
   * Returns the ID of this stanza.
   * <p>
   *   This property corresponds to the "id" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.3">RFC 6120</a>
   */
  String getId();

  /**
   * Returns the {@link Type} of this {@link Stanza}.
   * <p>
   *   This property corresponds to the "type" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.4">RFC 6120</a>
   */
  Type getType();
}