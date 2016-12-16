package viska.xmpp;

/**
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public interface Stanza {

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
  public Jid getRecipient();

  /**
   * Returns the sender of this stanza.
   * <p>
   *   This property corresponds to the "from" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.2">RFC 6120</a>
   */
  public Jid getSender();

  /**
   * Returns the ID of this stanza.
   * <p>
   *   This property corresponds to the "id" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.3">RFC 6120</a>
   */
  public String getId();

  /**
   * Returns the type of this stanza.
   * <p>
   *   This property corresponds to the "type" attribute of a stanza.
   * </p>
   * @see <a href="https://tools.ietf.org/html/rfc6120#section-8.1.4">RFC 6120</a>
   */
  public String getType();
}