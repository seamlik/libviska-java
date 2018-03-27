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

import chat.viska.commons.DomUtils;
import chat.viska.commons.EnumUtils;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
   * Gets the local name of the sub-element of this {@code <iq/>}.
   */
  default String getIqName() {
    final Element iqElement = (Element) toXml().getDocumentElement().getFirstChild();
    return iqElement == null ? "" : iqElement.getLocalName();
  }

  /**
   * Gets the namespace URI of te sub-element of this {@code <iq/>}.
   */
  default String getIqNamespace() {
    final Element iqElement = (Element) toXml()
        .getDocumentElement()
        .getFirstChild();
    if (iqElement == null) {
      return "";
    } else {
      final String namespace = iqElement.getNamespaceURI();
      return namespace == null ? "" : namespace;
    }
  }

  /**
   * Gets the main {@link Element} contained by this {@link <iq/>}.
   * @throws StreamErrorException If the XML is invalid.
   */
  default Element getIqElement(final String namespace, final String name)
      throws StreamErrorException {
    if (getType() != Type.IQ) {
      throw new IllegalStateException();
    }
    final Optional<Element> queryElement = DomUtils
        .convertToList(toXml().getDocumentElement().getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> name.equals(it.getLocalName()))
        .filter(it -> namespace.equals(it.getNamespaceURI()))
        .findFirst();
    if (!queryElement.isPresent()) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Malformed <iq/>."
      );
    } else {
      return queryElement.get();
    }
  }
}