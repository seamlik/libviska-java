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
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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

  private final Document document;

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
  public Stanza(@NonNull final Document document) {
    Objects.requireNonNull(document, "`document` is absent.");
    this.document = document;
  }

  /**
   * Gets the XML data.
   */
  @NonNull
  public Document getDocument() {
    return document;
  }

  @NonNull
  public String getId() {
    return document.getDocumentElement().getAttribute("id");
  }

  @Nullable
  public Jid getRecipient() {
    return new Jid(document.getDocumentElement().getAttribute("to"));
  }

  @Nullable
  public Jid getSender() {
    return new Jid(document.getDocumentElement().getAttribute("from"));
  }

  @NonNull
  public Type getType() {
    return EnumUtils.fromXmlValue(
        Type.class,
        document.getDocumentElement().getLocalName()
    );
  }

  @Nullable
  public IqType getIqType() {
    return EnumUtils.fromXmlValue(
        IqType.class,
        document.getDocumentElement().getAttribute("type")
    );
  }

  @NonNull
  public String getIqName() {
    final Element iqElement = (Element) this.document
        .getDocumentElement()
        .getFirstChild();
    if (iqElement == null) {
      return "";
    } else {
      return iqElement.getLocalName();
    }
  }

  @NonNull
  public String getIqNamespace() {
    final Element iqElement = (Element) this.document
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
      return null;
    }
    throw new UnsupportedOperationException();
  }
}