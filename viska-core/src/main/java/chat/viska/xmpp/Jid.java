package chat.viska.xmpp;

import java.nio.charset.StandardCharsets;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.InetAddressValidator;
import rocks.xmpp.precis.InvalidCodePointException;
import rocks.xmpp.precis.PrecisProfiles;

/**
 * An XMPP identifier.
 *
 * <p>
 *   Also known as Jabber Identifier, an JID is an address for locating an XMPP
 *   entity. A typical example of a JID would be
 *   "{@literal local@domain/resource}".
 * </p>
 * <p>
 *   A JID usually consists of 3 parts: local part, domain part and resource
 *   part. The local part refers to the user name of an XMPP account, the domain
 *   part refers to an XMPP server, and the resource part refers to a client
 *   connected to the server and logged in with the account.
 * </p>
 * <p>
 *   This class is immutable and will check whether the JID would be valid
 *   before it is created. Thus, any instances of a JID is valid.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622">RFC 7622</a>
 * @author Kai-Chung Yan (殷啟聰)
 * @since 0.1
 */
public class Jid {

  /**
   * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.3.1">RFC 7622</a>
   */
  public static final char[] localpartExcludedChars = {
    '\"',
    '&',
    '\'',
    '/',
    ':',
    '<',
    '>',
    '@'
  };

  private String localpart = "";
  private String domainpart = "";
  private String resourcepart = "";

  /**
   * Validates the local part of a Jid.
   * <p>
   *   The reason why a local part is invalid can be found in the documentation
   *   of each exceptions it throws.
   * </p>
   * @return {@code true} if the local part is valid.
   */
  public static boolean validateLocalpart(String localpart)
      throws InvalidCodePointException,
             InvalidJidPartException,
             JidTooLongException {
    if (localpart == null) {
      return true;
    }
    if (localpart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException(
        "The local part `" + localpart + "` is too long."
      );
    }
    for (char it : localpartExcludedChars) {
      if (localpart.indexOf(it) >= 0) {
        throw new InvalidJidPartException();
      }
    }
    PrecisProfiles.USERNAME_CASE_MAPPED.prepare(localpart);
    return true;
  }

  /**
   * Validates the domain part of a JID.
   * @return {@code true} if the domain part is valid.
   */
  public static boolean validateDomainpart(String domainpart)
      throws JidTooLongException, InvalidJidPartException {
    if (domainpart == null || domainpart.isEmpty()) {
      throw new InvalidJidPartException("Empty domain name!");
    }
    if (domainpart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException(domainpart);
    }
    boolean isDomainName = true /* DomainValidator.getInstance(true).isValid(domainpart) */;
    boolean isIpAddress = InetAddressValidator.getInstance().isValid(domainpart);
    if (!isDomainName && !isIpAddress) {
      throw new InvalidJidPartException(domainpart);
    }
    return true;
  }

  /**
   * Validates the resource part of a JID.
   * @return {@code true} if the resource part is valid.
   */
  public static boolean validateResourcepart(String resourcepart)
      throws InvalidCodePointException, JidTooLongException {
    if (resourcepart == null) {
      return true;
    }
    if (resourcepart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException(
        "The resource part `" + resourcepart + "` is too long."
      );
    }
    PrecisProfiles.OPAQUE_STRING.prepare(resourcepart);
    return true;
  }

  /**
   * Parses a raw JID {@link String} and returns the parts of the JID.
   * @return An array of {@link String} containing the local part, domain part
   *         and the resource part in order.
   */
  public static String[] parse(String rawJid) throws InvalidJidSyntaxException {
    if (rawJid.startsWith("<") && rawJid.endsWith(">")) {
      rawJid = rawJid.substring(1, rawJid.length() - 1);
    }
    int indexOfAt = rawJid.indexOf("@");
    int indexOfSlash = rawJid.indexOf("/");
    String[] result = new String[3];
    if (indexOfSlash > 0) {
      result[2] = rawJid.substring(indexOfSlash + 1, rawJid.length());
      rawJid = rawJid.substring(0, indexOfSlash);
    } else if (indexOfSlash == 0) {
      throw new InvalidJidSyntaxException();
    } else if (indexOfSlash < 0) {
      result[2] = "";
    }
    if (indexOfAt > 0) {
      result[0] = rawJid.substring(0, indexOfAt);
      result[1] = rawJid.substring(indexOfAt + 1);
    } else if (indexOfAt == 0) {
      throw new InvalidJidSyntaxException();
    } else if (indexOfAt < 0) {
      result[0] = "";
      result[1] = rawJid;
    }
    return result;
  }

  /**
   * Constructs a new JID using 3 specified parts of the JID.
   * @param parts {@link String}s representing the local part, domain part and
   *              and the resource part in order. Any redundant arguments are
   *              ignored.
   */
  public Jid(String... parts) throws InvalidJidPartException,
                                     InvalidJidSyntaxException,
                                     JidTooLongException {
    if (parts.length < 3) {
      throw new InvalidJidSyntaxException();
    }
    if (validateLocalpart(parts[0]) && validateDomainpart(parts[1])
                                    && validateResourcepart(parts[2])) {
      localpart = PrecisProfiles.USERNAME_CASE_MAPPED.enforce(parts[0]);
      domainpart = parts[1].trim();
      resourcepart = PrecisProfiles.OPAQUE_STRING.enforce(parts[2]);
    }
  }

  /**
   * Returns the local part of this JID.
   * @return the local part, never {@code null}.
   */
  public String getLocalpart() {
    return localpart;
  }

  /**
   * Returns the domain part of this JID.
   * @return never {@code null}.
   */
  public String getDomainpart() {
    return domainpart;
  }

  /**
   * Returns the resource part of this JID.
   * @return never {@code null}.
   */
  public String getResourcepart() {
    return resourcepart;
  }

  /**
   * Returns the {@link String} representation of this JID.
   * @return never {@code null}.
   */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(domainpart);
    if (!localpart.isEmpty()) {
      result.insert(0, '@').insert(0, localpart);
    }
    if (!resourcepart.isEmpty()) {
      result.append('/').append(resourcepart);
    }
    return result.toString();
  }

  /**
   * Returns if this JID equals the specified JID.
   * @return {@code true} if all parts of the JIDs are identical, {@code false}
   *         otherwise.
   */
  @Override
  public boolean equals(Object object) {
    if (object == null) {
      return false;
    }
    if (!(object instanceof Jid)) {
      return false;
    }
    Jid jid = (Jid)object;
    return localpart.equals(jid.localpart) && domainpart.equals(jid.domainpart)
                                           && resourcepart.equals(jid.resourcepart);
  }
}