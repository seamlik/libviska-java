package viska.xmpp;

/**
 * Thrown to indicate one part of a JID is invalid.
 * <p>
 *   There are 3 parts of a {@link Jid}: local, domain and resource.
 * </p>
 * <p>
 *   The local part must be an instance of the
 *   <a href="https://tools.ietf.org/html/rfc7613#section-3.2">
 *   UsernameCaseMapped profile of the PRECIS IdentifierClass</a>. Addtionally,
 *   the local part must not contain any characters belonging to
 *   {@link Jid#localpartExcludedChars}.
 * </p>
 * <p>
 *   The domain part is the required component of a {@link Jid}, therefore must
 *   not be empty. Additionally, the domain part must be either an IP address or
 *   a domain name.
 * </p>
 * <p>
 *   The resource part must be an instance of the
 *   <a href="https://tools.ietf.org/html/rfc7613#section-4.2">OpaqueString
 *   profile of the PRECIS FreeformClass</a>.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.3">RFC 7622</a>
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class InvalidJidPartException extends Exception {

  /**
   * @see Exception#Exception().
   */
  public InvalidJidPartException() {
    super();
  }

  /**
   * @see Exception#Exception().
   */
  public InvalidJidPartException(String msg) {
    super(msg);
  }

  /**
   * @see Exception#Exception(Throwable).
   */
  public InvalidJidPartException(Throwable throwable) {
    super(throwable);
  }
}