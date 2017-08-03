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

import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Future;
import org.w3c.dom.Document;

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

  @NonNull
  private DiscoInfo consumeDiscoInfo(@NonNull final Document document)
      throws StanzaErrorException {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets the Jabber/XMPP ID.
   */
  @NonNull
  public Jid getJid() {
    return jid;
  }

  /**
   * Queries available features and {@link chat.viska.xmpp.DiscoInfo.Identity}s.
   * This method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Maybe<DiscoInfo> queryDiscoInfo() {
    final String id = UUID.randomUUID().toString();
    final String query = String.format(
        "<iq type=\"get\" to=\"%1s\" id=\"%2s\"><query xmlns=\"%3s#info\"/></iq>",
        getJid().toString(),
        id,
        CommonXmlns.XEP_SERVICE_DISCOVERY
    );
    return this.session
        .getInboundStanzaStream()
        .filter(stanza -> stanza.getId().equals(id))
        .firstElement()
        .map(stanza -> this.consumeDiscoInfo(stanza.getDocument()));
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
}