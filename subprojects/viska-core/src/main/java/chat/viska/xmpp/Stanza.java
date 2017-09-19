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
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Wraps a stanza and provides utility methods.
 */
public class Stanza {

  /**
   * Stanza type.
   */
  public enum Type {
    IQ,
    MESSAGE,
    PRESENCE
  }

  public enum IqType {
    ERROR,
    GET,
    RESULT,
    SET
  }

  private final Document xml;

  public static boolean isStanza(@Nullable final Document document) {
    if (document == null) {
      return false;
    }
    final String rootName = document.getDocumentElement().getLocalName();
    return "iq".equals(rootName)
        || "message".equals(rootName)
        || "presence".equals(rootName);
  }

  public static Document getIqTemplate(final IqType type,
                                       final String id,
                                       @Nullable final Jid recipient) {
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
    if (recipient != null) {
      xml.getDocumentElement().setAttribute("to", recipient.toString());
    }
    return xml;
  }

  /**
   * Default constructor.
   */
  public Stanza(@Nonnull final Document xml) {
    Objects.requireNonNull(xml, "`xml` is absent.");
    this.xml = xml;
  }

  /**
   * Gets the XML data.
   */
  @Nonnull
  public Document getXml() {
    return xml;
  }

  @Nonnull
  public String getId() {
    return xml.getDocumentElement().getAttribute("id");
  }

  @Nullable
  public Jid getRecipient() {
    return new Jid(xml.getDocumentElement().getAttribute("to"));
  }

  @Nullable
  public Jid getSender() {
    return new Jid(xml.getDocumentElement().getAttribute("from"));
  }

  @Nonnull
  public Type getType() {
    return EnumUtils.fromXmlValue(
        Type.class,
        xml.getDocumentElement().getLocalName()
    );
  }

  @Nullable
  public IqType getIqType() {
    return EnumUtils.fromXmlValue(
        IqType.class,
        xml.getDocumentElement().getAttribute("type")
    );
  }

  @Nonnull
  public String getIqName() {
    final Element iqElement = (Element) this.xml
        .getDocumentElement()
        .getFirstChild();
    if (iqElement == null) {
      return "";
    } else {
      return iqElement.getLocalName();
    }
  }

  @Nonnull
  public String getIqNamespace() {
    final Element iqElement = (Element) this.xml
        .getDocumentElement()
        .getFirstChild();
    if (iqElement == null) {
      return "";
    } else {
      final String namespace = iqElement.getNamespaceURI();
      return namespace == null ? "" : namespace;
    }
  }

  @Nullable
  public Document getResultTemplate() {
    if (getType() != Type.IQ) {
      throw new IllegalStateException("This stanza is not an <iq/>.");
    }
    if (getIqType() == IqType.RESULT || getIqType() == null) {
      throw new IllegalStateException("This stanza is already an <iq/> result.");
    }
    return getIqTemplate(
        IqType.RESULT,
        UUID.randomUUID().toString(),
        getSender()
    );
  }
}