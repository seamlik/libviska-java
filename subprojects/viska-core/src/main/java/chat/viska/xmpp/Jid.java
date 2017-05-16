/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import rocks.xmpp.precis.InvalidCodePointException;
import rocks.xmpp.precis.PrecisProfiles;

/**
 * An XMPP identifier.
 *
 * <p>
 *   Also known as Jabber Identifier, an JID is an address for locating an XMPP
 *   entity. A typical example of a JID would be
 *   "{@literal localPart@domainPart/resourcePart}".
 * </p>
 * <p>
 *   A JID usually consists of 3 parts: localPart part, domainPart part and resourcePart
 *   part. The localPart part refers to the user name of an XMPP account, the domainPart
 *   part refers to an XMPP server, and the resourcePart part refers to a client
 *   connected to the server and logged in with the account.
 * </p>
 * <p>
 *   This class is immutable and will not validate the JID before it is created.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622">RFC 7622</a>
 * @since 0.1
 */
public class Jid {

  /**
   * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.3.1">RFC 7622</a>
   */
  private static final char[] localpartExcludedChars = {
    '\"',
    '&',
    '\'',
    '/',
    ':',
    '<',
    '>',
    '@'
  };

  private final String localPart;
  private final String domainPart;
  private final String resourcePart;

  /**
   * Validates the local part of a Jid.
   */
  public static void validateLocalPart(final @NonNull String localPart)
      throws InvalidCodePointException,
             InvalidJidPartException,
             JidTooLongException {
    Objects.requireNonNull(localPart);
    if (localPart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException(localPart);
    }
    for (char it : localpartExcludedChars) {
      if (localPart.indexOf(it) >= 0) {
        throw new InvalidJidPartException();
      }
    }
    PrecisProfiles.USERNAME_CASE_MAPPED.prepare(localPart);
  }

  /**
   * Validates the domainpart of a JID.
   */
  public static void validateDomainPart(final @NonNull String domainPart)
      throws JidTooLongException {
    Objects.requireNonNull(domainPart);
    if (domainPart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException(domainPart);
    }
    // TODO: Validate if it is a domainPart or an IP address
  }

  /**
   * Validates the resourcepart of a JID.
   */
  public static void validateResourcePart(final @NonNull String resourcePart)
      throws InvalidCodePointException, JidTooLongException {
    Objects.requireNonNull(resourcePart);
    if (resourcePart.getBytes(StandardCharsets.UTF_8).length > 1023) {
      throw new JidTooLongException();
    }
    PrecisProfiles.OPAQUE_STRING.prepare(resourcePart);
  }

  /**
   * Parses a raw JID {@link String} and returns the parts of the JID.
   * @return An array of {@link String} containing the localPart part, domainPart part
   *         and the resourcePart part in order.
   * @throws InvalidJidSyntaxException If the value is not a valid {@link Jid}.
   */
  public static String[] parseJidParts(String rawJid) {
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
   * @param parts {@link String}s representing the localPart part, domainPart part and
   *              and the resourcePart part in order. The array must contains at
   *              at least 3 elements and any redundant elements are ignored.
   *              In order to omit any part of the {@link Jid}, place a
   *              {@code null} at the corresponding position.
   * @throws InvalidJidSyntaxException If {@code parts} has less than 3 elements.
   */
  private Jid(String[] parts) {
    if (parts.length < 3) {
      throw new InvalidJidSyntaxException();
    }
    localPart = (parts[0] == null) ? "" : parts[0];
    domainPart = (parts[1] == null) ? "" : parts[1];
    resourcePart = (parts[2] == null) ? "" : parts[2];
  }

  public Jid(String localPart, String domainPart, String resourcePart) {
    this.localPart = (localPart == null) ? "" : localPart;
    this.domainPart = (domainPart == null) ? "" : domainPart;
    this.resourcePart = (resourcePart == null) ? "" : resourcePart;
  }

  public Jid(String jid) {
    this(parseJidParts(jid));
  }

  /**
   * Returns the {@link String} representation of this JID.
   * @return never {@code null}.
   */
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder(domainPart);
    if (!localPart.isEmpty()) {
      result.insert(0, '@').insert(0, localPart);
    }
    if (!resourcePart.isEmpty()) {
      result.append('/').append(resourcePart);
    }
    return result.toString();
  }

  /**
   * Returns if this JID equals the specified JID.
   * @return {@code true} if all parts of the JIDs are identical, {@code false}
   *         otherwise.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Jid that = (Jid)obj;
    return Objects.equals(localPart, that.localPart)
        && Objects.equals(domainPart, that.domainPart)
        && Objects.equals(resourcePart, that.resourcePart);
  }

  @Override
  public int hashCode() {
    return Objects.hash(localPart, domainPart, resourcePart);
  }

  /**
   * Returns the localPart part of this JID.
   * @return never {@code null}.
   */
  public String getLocalPart() {
    return localPart;
  }

  /**
   * Returns the domainPart part of this JID.
   * @return {@code null}.
   */
  public String getDomainPart() {
    return domainPart;
  }

  /**
   * Returns the resourcePart part of this JID.
   * @return never {@code null}.
   */
  public String getResourcePart() {
    return resourcePart;
  }

  public Jid toBareJid() {
    return resourcePart.isEmpty() ? this : new Jid(localPart, domainPart, "");
  }
}