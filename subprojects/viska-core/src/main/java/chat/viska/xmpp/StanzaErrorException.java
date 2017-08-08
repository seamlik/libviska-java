/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.xmpp;

import chat.viska.commons.EnumUtils;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Indicates a stanza error is received.
 */
public class StanzaErrorException extends Exception {

  /**
   * Type of a stanza error.
   */
  public enum Type {

    /**
     * Indicates the client should retry after providing credentials.
     */
    AUTH,

    /**
     * Indicates the entity has cancel the operation.
     */
    CANCEL,

    /**
     * Indicates a warning and that the client may proceed.
     */
    CONTINUE,

    /**
     * Indicates the client should retry after changing the data sent.
     */
    MODIFY,

    /**
     * Indicates the error is temporary and the client should retry after
     * waiting.
     */
    WAIT
  }

  /**
   * Condition of a stanza error.
   */
  public enum Condition {

    /**
     * Indicates the sent stanza does not conform to the schema and can not be
     * processed.
     */
    BAD_REQUEST,

    /**
     * Indicates access cannot be granted because an existing resource exists
     * with the same name or address.
     */
    CONFLICT,

    /**
     * Indicates the feature represented in the XML stanza is not implemented by
     * the intended recipient or an intermediate server and therefore the stanza
     * cannot be processed.
     */
    FEATURE_NOT_IMPLEMENTED,

    /**
     * Indicates the requesting entity does not possess the necessary
     * permissions to perform an action that only certain authorized roles or
     * individuals are allowed to complete.
     */
    FORBIDDEN,

    /**
     * Indicates the recipient or server can no longer be contacted at this
     * address, typically on a permanent basis.
     */
    GONE,

    /**
     * Indicates the server has experienced a misconfiguration or other internal
     * error that prevents it from processing the stanza.
     */
    INTERNAL_SERVER_ERROR,

    /**
     * Indicates the addressed JID or item requested cannot be found.
     */
    ITEM_NOT_FOUND,

    /**
     * Indicates the stanza provided a malformed {@link Jid}.
     */
    JID_MALFORMED,

    /**
     * Indicates the request does not meet criteria defined by the recipient or
     * server.
     */
    NOT_ACCEPTABLE,

    /**
     * Indicates the recipient or server does not allow any entity to perform
     * the action.
     */
    NOT_ALLOWED,

    /**
     * Indicates the sender needs to provide credentials before being allowed to
     * perform the action, or has provided improper credentials.
     */
    NOT_AUTHORIZED,

    /**
     * Indicates the entity has violated some local service policy.
     */
    POLICY_VIOLATION,

    /**
     * Indicates the intended recipient is temporarily unavailable.
     */
    RECIPIENT_UNAVAILABLE,

    /**
     * Indicates the recipient or server is redirecting requests for this
     * information to another entity, typically in a temporary fashion.
     */
    REDIRECT,

    /**
     * Indicates the requesting entity is not authorized to access the requested
     * service because prior registration is necessary.
     */
    REGISTRATION_REQUIRED,

    /**
     * Indicates a remote server or service specified as part or all of the
     * {@link Jid}s of the intended recipient does not exist or cannot be
     * resolved.
     */
    REMOTE_SERVER_NOT_FOUND,

    /**
     * Indicates a remote server or service specified as part or all of the
     * {@link Jid}s of the intended recipient (or needed to fulfill a request)
     * was resolved but communications could not be established within a
     * reasonable amount of time.
     */
    REMOTE_SERVER_TIMEOUT,

    /**
     * Indicates the server or recipient is busy or lacks the system resources
     * necessary to service the request.
     */
    RESOURCE_CONSTRAINT,

    /**
     * Indicates the server or recipient does not currently provide the
     * requested service.
     */
    SERVICE_UNAVAILABLE,

    /**
     * Indicates the requesting entity is not authorized to access the requested
     * service because a prior subscription is necessary.
     */
    SUBSCRIPTION_REQUIRED,

    /**
     * Indicates the error condition is not one of those defined by
     * <a href="https://datatracker.ietf.org/doc/rfc6120">RFC 6120</a>.
     */
    UNDEFINED_CONDITION,

    /**
     * Indicates the recipient or server understood the request but was not
     * expecting it at this time.
     */
    UNEXPECTED_REQUEST,

  }

  private final Stanza.Type stanzaType;
  private final String id;
  private final Jid originalSender;
  private final Jid intendedRecipient;
  private final Jid errorGenerator;
  private final URI redirect;
  private final Type type;
  private final Condition condition;
  private final String text;
  private final Element appCondition;
  private final Element stanza;

  /**
   * Generates an instance of this type based on the XML data of a stanza error.
   * @throws IllegalArgumentException If the XML data is malformed or does nor
   *         conform to <a href="https://datatracker.ietf.org/doc/rfc6120">RFC
   *         6120</a>.
   */
  @NonNull
  public static StanzaErrorException fromXml(@NonNull final Document document)
      throws StreamErrorException {
    final String stanzaNs = document.getDocumentElement().getNamespaceURI();
    if (!CommonXmlns.STANZA_CLIENT.equals(stanzaNs)
        && !CommonXmlns.STANZA_SERVER.equals(stanzaNs)) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Incorrect stanza namespace."
      );
    }
    final Element errorElement = (Element) document
        .getDocumentElement()
        .getElementsByTagName("error")
        .item(0);
    final Element conditionElement = (Element) errorElement
        .getChildNodes()
        .item(0);
    final boolean hasText = errorElement
        .getElementsByTagNameNS(CommonXmlns.STREAM_CONTENT, "text")
        .getLength() != 0;
    final Element textElement = hasText
        ? (Element) errorElement
              .getElementsByTagNameNS(
                  CommonXmlns.STREAM_CONTENT,
                  "text"
              )
              .item(0)
        : null;
    final boolean hasAppCondition =
        (hasText && errorElement.getChildNodes().getLength() == 3)
        || (!hasText && errorElement.getChildNodes().getLength() == 2);
    final boolean hasStanza = document
        .getDocumentElement()
        .getChildNodes()
        .getLength() == 2;

    final Stanza.Type stanzaType = EnumUtils.fromXmlValue(
        Stanza.Type.class,
        document.getDocumentElement().getLocalName()
    );
    final String id = document.getDocumentElement().getAttribute("id");
    final Jid originalSender = new Jid(
        document.getDocumentElement().getAttribute("to")
    );
    final Jid intendedRecipient = new Jid(
        document.getDocumentElement().getAttribute("from")
    );
    final Jid errorGenerator = new Jid(errorElement.getAttribute("by"));
    final Type type = EnumUtils.fromXmlValue(
        Type.class,
        errorElement.getAttribute("type")
    );
    final Condition condition;
    try {
      condition = EnumUtils.fromXmlValue(
          Condition.class,
          conditionElement.getTagName()
      );
    } catch (Exception ex) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "No condition element found."
      );
    }
    if (!CommonXmlns.STREAM_CONTENT.equals(conditionElement.getNamespaceURI())) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Incorrect condition namespace."
      );
    }
    final boolean hasRedirect =
        (condition == Condition.GONE || condition == Condition.REDIRECT)
        && !conditionElement.getTextContent().isEmpty();
    final URI redirect;
    try {
      redirect = hasRedirect
          ? new URI(conditionElement.getTextContent())
          : null;
    } catch (URISyntaxException ex) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Malformed redirect URI."
      );
    }
    final String text = hasText ? textElement.getTextContent() : null;
    final Element appCondition = hasAppCondition
        ? (Element) errorElement.getChildNodes().item(hasText ? 3 : 2)
        : null;
    final Element stanza = hasStanza
        ? (Element) document.getDocumentElement().getChildNodes().item(0)
        : null;

    return new StanzaErrorException(
        stanzaType,
        id,
        originalSender,
        intendedRecipient,
        condition, type, text, errorGenerator,
        redirect,
        appCondition,
        stanza
    );
  }

  public StanzaErrorException(@NonNull final Stanza stanza,
                              @NonNull final Condition condition,
                              @NonNull final Type type,
                              @Nullable final String text,
                              @Nullable final Jid errorGenerator,
                              @Nullable final URI redirect,
                              @Nullable final Element appCondition) {
    this(
        stanza.getType(),
        stanza.getId(),
        stanza.getSender(),
        stanza.getRecipient(),
        condition, type, text, errorGenerator,
        redirect,
        appCondition,
        null
    );
  }

  /**
   * Default constructor.
   */
  public StanzaErrorException(@NonNull final Stanza.Type stanzaType,
                              @NonNull final String id,
                              @NonNull final Jid originalSender,
                              @NonNull final Jid intendedRecipient,
                              @NonNull final Condition condition,
                              @NonNull final Type errorType,
                              @Nullable final String text,
                              @Nullable final Jid errorGenerator,
                              @Nullable final URI redirect,
                              @Nullable final Element appCondition,
                              @Nullable final Element stanza) {
    super("[" + EnumUtils.toXmlValue(condition) + "] " + text);
    this.stanzaType = stanzaType;
    this.id = id == null ? "" : id;
    this.originalSender = originalSender;
    this.intendedRecipient = intendedRecipient;
    this.errorGenerator = errorGenerator;
    this.redirect = redirect;
    this.type = errorType;
    this.condition = condition;
    this.appCondition = appCondition;
    this.stanza = stanza;
    this.text = text == null ? "" : text;

    Objects.requireNonNull(stanzaType, "`stanzaType` is absent.");
    Objects.requireNonNull(id, "`id` is absent.");
    Objects.requireNonNull(originalSender, "`originalSender` is absent.");
    Objects.requireNonNull(intendedRecipient, "`intendedRecipient` is absent.");
    Objects.requireNonNull(condition, "`condition` is absent.");
    Objects.requireNonNull(errorType, "`errorType` is absent.");
  }

  /**
   * Gets the stanza ID.
   */
  @NonNull
  public String getId() {
    return id;
  }

  /**
   * Gets the stanza sender.
   */
  @Nullable
  public Jid getOriginalSender() {
    return originalSender;
  }

  /**
   * Gets the stanza recipient.
   */
  @Nullable
  public Jid getIntendedRecipient() {
    return intendedRecipient;
  }

  /**
   * Gets the application-specific condition.
   */
  @Nullable
  public Element getApplicationCondition() {
    return appCondition;
  }

  /**
   * Gets the error type.
   */
  @Nullable
  public Type getType() {
    return type;
  }

  /**
   * Gets the error condition.
   */
  @Nullable
  public Condition getCondition() {
    return condition;
  }

  /**
   * Gets the descriptive text.
   */
  @NonNull
  public String getText() {
    return text;
  }

  /**
   * Gets the original sent stanza.
   */
  @Nullable
  public Element getStanza() {
    return stanza;
  }

  /**
   * Gets the actual entity that detects and thus returns this error.
   */
  @Nullable
  public Jid getErrorGenerator() {
    return errorGenerator;
  }

  /**
   * Gets the redirected {@link URI}. It serves as the <em>gone URI</em> when
   * the condition is {@link Condition#GONE}.
   */
  @Nullable
  public URI getRedirect() {
    return redirect;
  }

  /**
   * Gets the stanza type.
   */
  @Nullable
  public Stanza.Type getStanzaType() {
    return stanzaType;
  }

  @NonNull
  public Document toXml() {
    throw new UnsupportedOperationException();
  }
}