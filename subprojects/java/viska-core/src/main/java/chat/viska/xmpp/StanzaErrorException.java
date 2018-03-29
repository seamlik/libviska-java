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
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Indicates a stanza error has occurred is received.
 */
public class StanzaErrorException extends Exception {

  /**
   * Type of a stanza error.
   */
  public enum Type {
    AUTH,
    CANCEL,
    CONTINUE,
    MODIFY,
    WAIT
  }

  /**
   * Condition of a stanza error.
   */
  public enum Condition {
    BAD_REQUEST,
    CONFLICT,
    FEATURE_NOT_IMPLEMENTED,
    FORBIDDEN,
    GONE,
    INTERNAL_SERVER_ERROR,
    ITEM_NOT_FOUND,
    JID_MALFORMED,
    NOT_ACCEPTABLE,
    NOT_ALLOWED,
    NOT_AUTHORIZED,
    POLICY_VIOLATION,
    RECIPIENT_UNAVAILABLE,
    REDIRECT,
    REGISTRATION_REQUIRED,
    REMOTE_SERVER_NOT_FOUND,
    REMOTE_SERVER_TIMEOUT,
    RESOURCE_CONSTRAINT,
    SERVICE_UNAVAILABLE,
    SUBSCRIPTION_REQUIRED,
    UNDEFINED_CONDITION,
    UNEXPECTED_REQUEST,
  }

  private final Stanza.Type stanzaType;
  private final String id;
  private final Jid sender;
  private final Jid recipient;
  private final Jid errorGenerator;
  private final String redirect;
  private final Type type;
  private final Condition condition;
  private final String text;
  private final @Nullable Element appCondition;
  private final @Nullable Element stanza;

  /**
   * Generates an instance of this type based on the XML data of a stanza error.
   * @throws StreamErrorException If the XML data is malformed or does nor
   *         conform to <a href="https://datatracker.ietf.org/doc/rfc6120">RFC
   *         6120</a>.
   */
  public static StanzaErrorException fromXml(final Document document) throws StreamErrorException {
    final Element errorElement = (Element) document
        .getDocumentElement()
        .getElementsByTagName("error")
        .item(0);
    final Element conditionElement = (Element) errorElement
        .getChildNodes()
        .item(0);
    final Element textElement = (Element) errorElement.getElementsByTagNameNS(
        CommonXmlns.STANZA_ERROR, "text"
    ).item(0);
    final boolean hasAppCondition =
        (textElement != null && errorElement.getChildNodes().getLength() == 3)
        || (textElement == null && errorElement.getChildNodes().getLength() == 2);
    final boolean hasStanza = document
        .getDocumentElement()
        .getChildNodes()
        .getLength() == 2;

    final Stanza.Type stanzaType = EnumUtils.fromXmlValue(
        Stanza.Type.class,
        document.getDocumentElement().getLocalName()
    );
    final String id = document.getDocumentElement().getAttribute("id");
    final Jid recipient = new Jid(
        document.getDocumentElement().getAttribute("to")
    );
    final Jid sender = new Jid(
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
    if (!CommonXmlns.STANZA_ERROR.equals(conditionElement.getNamespaceURI())) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Incorrect condition namespace."
      );
    }
    final String redirect = conditionElement.getTextContent();
    final String text = textElement == null ? "" : textElement.getTextContent();
    final Element appCondition = hasAppCondition
        ? (Element) errorElement.getChildNodes().item(textElement != null ? 3 : 2)
        : null;
    final Element stanza = hasStanza
        ? (Element) document.getDocumentElement().getChildNodes().item(0)
        : null;

    return new StanzaErrorException(
        stanzaType,
        id,
        sender,
        recipient,
        condition,
        type,
        text,
        errorGenerator,
        redirect,
        appCondition,
        stanza
    );
  }

  /**
   * Default constructor.
   */
  private StanzaErrorException(final Stanza.Type stanzaType,
                               final String id,
                               final Jid sender,
                               final Jid recipient,
                               final Condition condition,
                               final Type errorType,
                               final String text,
                               final Jid errorGenerator,
                               final String redirect,
                               @Nullable final Element appCondition,
                               @Nullable final Element stanza) {
    super(
        "[" + EnumUtils.toXmlValue(condition) + "]"
            + (text == null ? "" : " " + text)
    );
    this.stanzaType = stanzaType;
    this.id = id;
    this.sender = sender;
    this.recipient = recipient;
    this.errorGenerator = errorGenerator;
    this.redirect = redirect;
    this.type = errorType;
    this.condition = condition;
    this.appCondition = appCondition;
    this.stanza = stanza;
    this.text = text;
  }

  /**
   * Gets the stanza ID.
   */
  public String getId() {
    return id;
  }

  /**
   * Gets the sender.
   */
  public Jid getSender() {
    return sender;
  }

  /**
   * Gets the recipient.
   */
  public Jid getRecipient() {
    return recipient;
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
   * Gets the redirection.
   */
  public String getRedirect() {
    return redirect;
  }

  /**
   * Gets the stanza type.
   */
  public Stanza.Type getStanzaType() {
    return stanzaType;
  }

  public Document toXml() {
    throw new UnsupportedOperationException();
  }
}