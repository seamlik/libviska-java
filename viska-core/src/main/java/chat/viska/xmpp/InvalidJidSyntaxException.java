package chat.viska.xmpp;

/**
 * Thrown to indicate the syntax of a raw JID string is invalid.
 */
public class InvalidJidSyntaxException extends Exception {

  /**
   * @see Exception#Exception().
   */
  public InvalidJidSyntaxException() {
    super();
  }

  /**
   * @see Exception#Exception().
   */
  public InvalidJidSyntaxException(String msg) {
    super(msg);
  }
}
