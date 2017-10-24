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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Raw XML data wrapper.
 */
public class XmlWrapperStanza extends Stanza {

  private final Document xml;

  /**
   * Checks if a {@link Document} is a stanza.
   */
  public static boolean isStanza(@Nullable final Document document) {
    if (document == null) {
      return false;
    }
    final String rootName = document.getDocumentElement().getLocalName();
    return "iq".equals(rootName)
        || "message".equals(rootName)
        || "presence".equals(rootName);
  }

  /**
   * Default constructor.
   */
  public XmlWrapperStanza(@Nonnull final Document xml) {
    this.xml = xml;
  }

  @Nonnull
  @Override
  public Document getXml() {
    return xml;
  }

  @Override
  @Nonnull
  public String getId() {
    return xml.getDocumentElement().getAttribute("id");
  }

  @Override
  @Nonnull
  public Jid getRecipient() {
    return new Jid(xml.getDocumentElement().getAttribute("to"));
  }

  @Override
  @Nonnull
  public Jid getSender() {
    return new Jid(xml.getDocumentElement().getAttribute("from"));
  }

  @Override
  @Nonnull
  public Type getType() {
    final Type type = EnumUtils.fromXmlValue(
        Type.class,
        xml.getDocumentElement().getLocalName()
    );
    if (type == null) {
      throw new IllegalArgumentException();
    }
    return type;
  }

  @Override
  @Nullable
  public IqType getIqType() {
    return EnumUtils.fromXmlValue(
        IqType.class,
        xml.getDocumentElement().getAttribute("type")
    );
  }

  @Override
  @Nonnull
  public String getIqName() {
    final Element iqElement = (Element) this.xml.getDocumentElement().getFirstChild();
    if (iqElement == null) {
      return "";
    } else {
      return iqElement.getLocalName();
    }
  }

  @Override
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
}