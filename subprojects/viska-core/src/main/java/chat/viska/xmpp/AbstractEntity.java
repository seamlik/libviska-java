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
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * XMPP entity.
 */
public abstract class AbstractEntity implements SessionAware {

  private final Session session;
  private final Jid jid;

  protected AbstractEntity(@NonNull final Session session,
                           @NonNull final Jid jid) {
    Objects.requireNonNull(session);
    Objects.requireNonNull(jid);
    this.session = session;
    this.jid = jid;
  }

  @Nullable
  private DiscoInfo consumeDiscoInfo(@NonNull final Stanza stanza)
      throws StanzaErrorException {
    if (stanza.getIqType() == Stanza.IqType.ERROR) {
      throw new StanzaErrorException(
          stanza,
          StanzaErrorException.Condition.BAD_REQUEST,
          StanzaErrorException.Type.MODIFY,
          "No IQ element found.", null,
          null,
          null
      );
    }
    final String xmlns = CommonXmlns.XEP_SERVICE_DISCOVERY + "#info";
    final Element queryElement = (Element) stanza
        .getDocument()
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    if (queryElement == null) {
      getSession().send(
          new StanzaErrorException(
              stanza,
              StanzaErrorException.Condition.BAD_REQUEST,
              StanzaErrorException.Type.MODIFY,
              "No IQ element found.", null,
              null,
              null
          ).toXml()
      );
      return null;
    }
    final List<DiscoInfo.Identity> identities = Observable
        .fromIterable(
            DomUtils.toList(
                queryElement.getElementsByTagNameNS(xmlns, "identity")
            )
        )
        .cast(Element.class)
        .map(DiscoInfo.Identity::fromXml)
        .toList()
        .blockingGet();
    final List<String> features = Observable
        .fromIterable(
            DomUtils.toList(
                queryElement.getElementsByTagNameNS(xmlns, "feature")
            )
        )
        .cast(Element.class)
        .map(it -> it.getAttribute("var"))
        .toList()
        .blockingGet();
    return new DiscoInfo(
        new HashSet<>(identities), new HashSet<>(features)
    );
  }

  /**
   * Gets the Jabber/XMPP ID.
   */
  @NonNull
  public Jid getJid() {
    return jid;
  }

  /**
   * Queries available features and {@link DiscoInfo.Identity}s.
   * @return Token to notify the completion. Emits {@code null} if the
   *         {@link Session} is disposed of or the received result is malformed
   *         XML data. Emits a {@link StanzaErrorException} if received a stanza
   *         error.
   */
  @NonNull
  public Maybe<DiscoInfo> queryDiscoInfo() {
    if (getSession().getState() == Session.State.DISPOSED) {
      return Maybe.never();
    }
    final String id = UUID.randomUUID().toString();
    final String query = String.format(
        "<iq type=\"get\" to=\"%1s\" id=\"%2s\"><query xmlns=\"%3s#info\"/></iq>",
        getJid().toString(),
        id,
        CommonXmlns.XEP_SERVICE_DISCOVERY
    );
    try {
      getSession().send(query);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
    return this.session
        .getInboundStanzaStream()
        .filter(it -> it.getId().equals(id))
        .firstElement()
        .map(this::consumeDiscoInfo);
  }

  /**
   * Queries other {@link AbstractEntity}s associated to this
   * {@link AbstractEntity}.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Future<DiscoItem> queryItems() {
    throw new UnsupportedOperationException();
  }


  /**
   * Queries information of the XMPP client software. This method is part of
   * <a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software
   * Version</a>.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Maybe<Map<String, String>> querySoftwareInfo() {
    return null;
  }

  @Override
  @NonNull
  public Session getSession() {
    return session;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AbstractEntity that = (AbstractEntity) obj;
    return Objects.equals(session, that.session)
        && Objects.equals(getJid(), that.getJid());
  }

  @Override
  public int hashCode() {
    return Objects.hash(session, getJid());
  }

  @Override
  public String toString() {
    return jid.toString();
  }
}