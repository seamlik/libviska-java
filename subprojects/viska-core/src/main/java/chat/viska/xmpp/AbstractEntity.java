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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * XMPP entity.
 */
public abstract class AbstractEntity implements SessionAware {


  /**
   * Item in associated with an {@link AbstractEntity}.
   */
  public static class Item {

    private final Jid jid;
    private final String name;
    private final String node;

    @NonNull
    static Item fromXml(@NonNull final Element element) {
      return new Item(
          new Jid(element.getAttribute("jid")),
          element.getAttribute("name"),
          element.getAttribute("node")
      );
    }

    public Item(@Nullable final Jid jid,
                @NonNull final String name,
                @NonNull final String node) {
      this.jid = jid;
      this.name = name == null ? "" : name;
      this.node = node == null ? "" : node;
    }

    /**
     * Gets the Jabber/XMPP ID.
     */
    @NonNull
    public Jid getJid() {
      return jid;
    }

    /**
     * Gets the name.
     */
    @NonNull
    public String getName() {
      return name;
    }

    @NonNull
    public String getNode() {
      return node;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Item item = (Item) obj;
      return Objects.equals(jid, item.jid)
          && Objects.equals(name, item.name)
          && Objects.equals(node, item.node);
    }

    @Override
    public int hashCode() {
      return Objects.hash(jid, name, node);
    }
  }

  private final Session session;

  protected AbstractEntity(@NonNull final Session session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  @Nullable
  private DiscoInfo convertToDiscoInfo(@NonNull final Stanza stanza)
      throws StanzaErrorException {
    //TODO: Localized identities
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
              "No IQ element found.",
              null,
              null,
              null
          ).toXml()
      );
      return null;
    }
    final List<DiscoInfo.Identity> identities = Observable
        .fromIterable(DomUtils.toList(
            queryElement.getElementsByTagNameNS(xmlns, "identity"))
        )
        .cast(Element.class)
        .map(DiscoInfo.Identity::fromXml)
        .toList()
        .blockingGet();
    final List<String> features = Observable
        .fromIterable(DomUtils.toList(
            queryElement.getElementsByTagNameNS(xmlns, "feature")
        ))
        .cast(Element.class)
        .map(it -> it.getAttribute("var"))
        .toList()
        .blockingGet();
    return new DiscoInfo(identities, features);
  }

  @Nullable
  private List<Item> convertToItems(@NonNull final Stanza stanza) {
    final String xmlns = CommonXmlns.XEP_SERVICE_DISCOVERY + "#items";
    final Element queryElement = (Element) stanza
        .getDocument()
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    return Observable
        .fromIterable(DomUtils.toList(
        queryElement.getElementsByTagNameNS(xmlns, "item")
        ))
        .cast(Element.class)
        .map(Item::fromXml)
        .toList()
        .blockingGet();
  }

  /**
   * Gets the Jabber/XMPP ID.
   */
  @NonNull
  public abstract Jid getJid();

  /**
   * Queries available features and {@link DiscoInfo.Identity}s.
   * @return Token to notify the completion. Emits {@code null} if the
   *         {@link Session} is disposed of or the received result is malformed
   *         XML data. Emits a {@link StanzaErrorException} if received a stanza
   *         error.
   */
  @NonNull
  public Maybe<DiscoInfo> queryDiscoInfo() {
    try {
      return getSession().query(
          CommonXmlns.XEP_SERVICE_DISCOVERY + "#info",
          getJid()
      ).map(this::convertToDiscoInfo);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Queries other {@link Item}s associated with this {@link AbstractEntity}.
   */
  @NonNull
  public Maybe<List<Item>> queryItems(@Nullable final String node) {
    try {
      return getSession().query(
          CommonXmlns.XEP_SERVICE_DISCOVERY + "#items",
          getJid()
      ).map(this::convertToItems);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
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
    return getJid().toString();
  }
}