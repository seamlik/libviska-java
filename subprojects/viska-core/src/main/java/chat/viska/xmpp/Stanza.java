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
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;

public class Stanza implements SessionAware {

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

  private final Session session;
  private final Document document;

  /**
   * Default constructor.
   */
  public Stanza(@NonNull final Session session,
                @NonNull final Document document) {
    this.session = session;
    this.document = document;

    Objects.requireNonNull(session, "`session` is absent.");
    Objects.requireNonNull(document, "`document` is absent.");
    final String rootNs = document.getDocumentElement().getNamespaceURI();
    Validate.isTrue(
        CommonXmlns.STANZA_CLIENT.equals(rootNs)
            || CommonXmlns.STANZA_SERVER.equals(rootNs),
        "Incorrect root namespace."
    );
    final String rootName = document.getDocumentElement().getLocalName();
    Validate.isTrue(
        "iq".equals(rootName) || "message".equals(rootName) || "presence".equals(rootName),
        "Document is not one of the stanza types."
    );
    Validate.isTrue(
        document.getDocumentElement().getChildNodes().getLength() <= 1,
        "Stanza has more than 1 children."
    );
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

  @Override
  @NonNull
  public Session getSession() {
    return session;
  }
}