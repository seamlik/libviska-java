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
import javax.xml.namespace.QName;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * XML data being transferred during a {@link Session}.
 */
public interface Stanza {

  /**
   * Stanza type.
   */
  enum Type {
    IQ,
    MESSAGE,
    PRESENCE
  }

  /**
   * Type of an {@code <iq/>}
   */
  enum IqType {
    ERROR,
    GET,
    RESULT,
    SET
  }

  /**
   * Gets the XML data.
   */
  Document toXml();

  /**
   * Gets the ID.
   */
  default String getId() {
    return toXml().getDocumentElement().getAttribute("id");
  }

  /**
   * Gets the recipient.
   */
  default Jid getRecipient() {
    return new Jid(toXml().getDocumentElement().getAttribute("to"));
  }

  /**
   * Gets the sender.
   */
  default Jid getSender() {
    return new Jid(toXml().getDocumentElement().getAttribute("from"));
  }

  /**
   * Gets the type.
   */
  default Type getType() {
    return EnumUtils.fromXmlValue(
        Type.class,
        toXml().getDocumentElement().getLocalName()
    );
  }

  /**
   * Gets the type of the {@code <iq/>}.
   */
  @Nullable
  default IqType getIqType() {
    return EnumUtils.fromXmlValue(
        IqType.class,
        toXml().getDocumentElement().getAttribute("type")
    );
  }

  /**
   * Gets the signature of an {@code <iq/>}.
   */
  default QName getIqQName() throws StreamErrorException {
    if (getType() != Type.IQ) {
      throw new IllegalArgumentException();
    }
    final Element iqElement = getIqElement();
    return new QName(iqElement.getNamespaceURI(), iqElement.getLocalName(), iqElement.getPrefix());
  }

  /**
   * Gets the main {@link Element} contained in this {@link <iq/>}.
   * @throws StreamErrorException If the XML is invalid.
   * @throws IllegalStateException If this {@link Stanza} is not an {@code <iq/>}.
   */
  default Element getIqElement() throws StreamErrorException {
    if (getType() != Type.IQ) {
      throw new IllegalStateException();
    }
    final @Nullable Node iqElement = toXml().getDocumentElement().getFirstChild();
    if (iqElement == null) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Empty <iq/>."
      );
    } else {
      return (Element) iqElement;
    }
  }
}