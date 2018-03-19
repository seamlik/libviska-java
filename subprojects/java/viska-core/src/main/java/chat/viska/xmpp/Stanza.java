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
public abstract class Stanza {

  /**
   * Stanza type.
   */
  public enum Type {
    IQ,
    MESSAGE,
    PRESENCE
  }

  /**
   * Type of an {@code <iq/>}
   */
  public enum IqType {
    ERROR,
    GET,
    RESULT,
    SET
  }

  /**
   * Generates a {@link Document} template for an {@code <iq/>}.
   */
  public static Document getIqTemplate(final IqType type,
                                       final String id,
                                       final Jid sender,
                                       final Jid recipient) {
    final String iq = String.format(
        "<iq type=\"%1s\" id=\"%2s\"></iq>",
        EnumUtils.toXmlValue(type),
        id
    );
    final Document xml;
    try {
      xml = DomUtils.readDocument(iq);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    if (!Jid.isEmpty(sender)) {
      xml.getDocumentElement().setAttribute("from", sender.toString());
    }
    if (!Jid.isEmpty(recipient)) {
      xml.getDocumentElement().setAttribute("to", recipient.toString());
    }
    return xml;
  }

  /**
   * Gets the XML data.
   */
  public abstract Document getXml();

  /**
   * Gets the ID.
   */
  public abstract String getId();

  /**
   * Gets the recipient.
   */
  public abstract Jid getRecipient();

  /**
   * Gets the sender.
   */
  public abstract Jid getSender();

  /**
   * Gets the type.
   */
  public abstract Type getType();

  /**
   * Gets the type of the {@code <iq/>}.
   */
  @Nullable
  public abstract IqType getIqType();

  /**
   * Gets the local name of the sub-element of this {@code <iq/>}.
   */
  public abstract String getIqName();

  /**
   * Gets the namespace URI of te sub-element of this {@code <iq/>}.
   */
  public abstract String getIqNamespace();

  /**
   * Gets the main {@link Element} contained by this {@link <iq/>}.
   * @throws StreamErrorException If the XML is invalid.
   */
  public Element getIqElement(final String namespace, String name) throws StreamErrorException {
    if (getType() != Type.IQ) {
      throw new IllegalStateException();
    }
    final Optional<Element> queryElement = DomUtils
        .convertToList(getXml().getDocumentElement().getChildNodes())
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

  /**
   * Generates a template of a result to this {@code <iq/>}.
   */
  public Document getResultTemplate() {
    if (getType() != Type.IQ) {
      throw new IllegalStateException("This stanza is not an <iq/>.");
    }
    if (getIqType() == IqType.RESULT || getIqType() == null) {
      throw new IllegalStateException("This stanza is already an <iq/> result.");
    }
    return getIqTemplate(
        IqType.RESULT,
        getId(),
        getRecipient(),
        getSender()
    );
  }
}