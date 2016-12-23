package chat.viska.xmpp;

import chat.viska.xmpp.Jid;

/**
 * Thrown to indicate that some part of a {@link Jid} is too long.
 * <p>
 *   According to the RFC, any part of a {@link Jid} must not excceed 1023 bytes
 *   after being encoded in UTF-8.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.1">RFC 7622</a>
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class JidTooLongException extends Exception {

  /**
   * @see Exception#Exception().
   */
  public JidTooLongException() {
    super();
  }

  /**
   * @see Exception#Exception(String).
   */
  public JidTooLongException(String msg) {
    super(msg);
  }
}
