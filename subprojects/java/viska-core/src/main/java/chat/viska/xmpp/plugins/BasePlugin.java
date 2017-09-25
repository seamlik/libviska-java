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

package chat.viska.xmpp.plugins;

import chat.viska.commons.DomUtils;
import chat.viska.commons.EnumUtils;
import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.Stanza;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Provides the most fundamental features of an XMPP session.. This plugin is
 * built-in and needs be applied manually.
 *
 * <h1>Supported XMPP Extensions</h1>
 * <ul>
 *   <li><a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software Version</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0199.html">XEP-0199: XMPP Ping</a></li>
 * </ul>
 */
public class BasePlugin implements Plugin {

  private static final Set<String> features = new HashSet<>(Arrays.asList(
      CommonXmlns.PING,
      CommonXmlns.SOFTWARE_VERSION
  ));
  private static final Set<Map.Entry<String, String>> SUPPORTED_IQS = new HashSet<>(Arrays.asList(
      new AbstractMap.SimpleImmutableEntry<>(
          CommonXmlns.SERVICE_DISCOVERY + "#info", "query"
      ),
      new AbstractMap.SimpleImmutableEntry<>(
          CommonXmlns.SERVICE_DISCOVERY + "#items", "query"
      ),
      new AbstractMap.SimpleImmutableEntry<>(
          CommonXmlns.SOFTWARE_VERSION, "query"
      ),
      new AbstractMap.SimpleImmutableEntry<>(CommonXmlns.ROSTER, "query")
  ));

  private final MutableReactiveObject<String> softwareName = new MutableReactiveObject<>("");
  private final MutableReactiveObject<String> softwareVersion = new MutableReactiveObject<>("");
  private final MutableReactiveObject<String> operatingSystem = new MutableReactiveObject<>("");
  private final MutableReactiveObject<String> softwareType = new MutableReactiveObject<>("");

  private Session session;

  @Nullable
  private static List<DiscoItem> convertToDiscoItems(@Nonnull final Document xml) {
    final String xmlns = CommonXmlns.SERVICE_DISCOVERY + "#items";
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("Not an <iq/>.");
    }
    return Observable
        .fromIterable(DomUtils.convertToList(
            queryElement.getElementsByTagNameNS(xmlns, "item")
        ))
        .cast(Element.class)
        .map(it -> new DiscoItem(
            new Jid(it.getAttribute("jid")),
            it.getAttribute("name"),
            it.getAttribute("node")
        ))
        .toList()
        .blockingGet();
  }

  @Nonnull
  private static DiscoInfo convertToDiscoInfo(@Nonnull final Document xml) {
    final String xmlns = CommonXmlns.SERVICE_DISCOVERY + "#info";
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("Not an <iq/>.");
    }
    final List<DiscoInfo.Identity> identities = Observable
        .fromIterable(DomUtils.convertToList(
            queryElement.getElementsByTagNameNS(xmlns, "identity"))
        )
        .cast(Element.class)
        .map(it -> new DiscoInfo.Identity(
            it.getAttribute("category"),
            it.getAttribute("name"),
            it.getAttribute("type")
        ))
        .toList()
        .blockingGet();
    final List<String> features = Observable
        .fromIterable(DomUtils.convertToList(
            queryElement.getElementsByTagNameNS(xmlns, "feature")
        ))
        .cast(Element.class)
        .map(it -> it.getAttribute("var"))
        .toList()
        .blockingGet();
    return new DiscoInfo(identities, features);
  }

  @Nonnull
  private static SoftwareInfo convertToSoftwareInfo(@Nonnull final Stanza stanza) {
    final Element queryElement = (Element) stanza
        .getXml()
        .getDocumentElement()
        .getElementsByTagNameNS(CommonXmlns.SOFTWARE_VERSION, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("Not an <iq/>.");
    }
    final Element nameElement = (Element)
        queryElement.getElementsByTagName("name").item(0);
    final Element versionElement = (Element)
        queryElement.getElementsByTagName("version").item(0);
    final Element osElement = (Element)
        queryElement.getElementsByTagName("os").item(0);
    return new SoftwareInfo(
        nameElement == null ? null : nameElement.getTextContent(),
        versionElement == null ? null : versionElement.getTextContent(),
        osElement == null ? null : osElement.getTextContent()
    );
  }

  private static List<RosterItem> convertToRosterItems(@Nonnull final Document xml) {
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(CommonXmlns.ROSTER, "query")
        .item(0);
    return Observable.fromIterable(
        DomUtils.convertToList(queryElement.getElementsByTagName("item"))
    ).cast(Element.class).map(it -> new RosterItem(
        new Jid(it.getAttribute("jid")),
        EnumUtils.fromXmlValue(
            RosterItem.Subscription.class,
            it.getAttribute("subscription")
        ),
        it.getAttribute("name"),
        Observable
            .fromIterable(DomUtils.convertToList(it.getElementsByTagName("group")))
            .map(Node::getTextContent)
            .toList().blockingGet()
    )).toList().blockingGet();
  }

  @Nonnull
  private Document getSoftwareVersionResult(@Nonnull final Jid recipient,
                                            @Nonnull final String id) {
    final Document result = Stanza.getIqTemplate(
        Stanza.IqType.RESULT,
        id,
        recipient
    );
    final Node queryElement = result.appendChild(result.createElementNS(
        CommonXmlns.SOFTWARE_VERSION,
        "query"
    ));
    queryElement
        .appendChild(result.createElement("name"))
        .setTextContent(this.softwareName.getValue());
    queryElement
        .appendChild(result.createElement("version"))
        .setTextContent(this.softwareVersion.getValue());
    queryElement
        .appendChild(result.createElement("os"))
        .setTextContent(this.operatingSystem.getValue());
    return result;
  }

  @Nonnull
  private Document getDiscoInfoResult(@Nonnull final Jid recipient,
                                      @Nonnull final String id) {
    final Document result = Stanza.getIqTemplate(
        Stanza.IqType.RESULT,
        id,
        recipient
    );
    final Node queryElement = result.appendChild(result.createElementNS(
        CommonXmlns.SERVICE_DISCOVERY + "#info",
        "query"
    ));
    final Element identityElement = (Element) queryElement.appendChild(
        result.createElement("identity")
    );
    identityElement.setAttribute("category", "client");
    identityElement.setAttribute("type", this.softwareType.getValue());
    identityElement.setAttribute("name", this.softwareName.getValue());
    Observable.fromIterable(
        getSession().getPluginManager().getPlugins()
    ).flatMap(
        it -> Observable.fromIterable(it.getFeatures())
    ).forEach(it -> {
      final Element featureElement = (Element)
          queryElement.appendChild(result.createElement("feature"));
      featureElement.setAttribute("var", it);
    });
    return result;
  }

  @Nonnull
  private Document getDiscoItemsResult(@Nonnull final Stanza query) {
    final Document result = query.getResultTemplate();
    final Element queryElement = result.createElementNS(
        CommonXmlns.SERVICE_DISCOVERY + "#items",
        "query"
    );
    final String node = (
        (Element) query.getXml().getDocumentElement().getFirstChild()
    ).getAttribute("node");
    if (!node.isEmpty()) {
      queryElement.setAttribute("node", node);
    }
    result.getDocumentElement().appendChild(queryElement);
    return result;
  }

  @Nonnull
  public MutableReactiveObject<String> getSoftwareName() {
    return softwareName;
  }

  @Nonnull
  public MutableReactiveObject<String> getSoftwareVersion() {
    return softwareVersion;
  }

  @Nonnull
  public MutableReactiveObject<String> getOperatingSystem() {
    return operatingSystem;
  }

  /**
   * Gets and sets the software type. Possible values are listed on the
   * <a href="https://xmpp.org/registrar/disco-categories.html#client">XMPP
   * registrar</a>.
   */
  @Nonnull
  public MutableReactiveObject<String> getSoftwareType() {
    return softwareType;
  }

  @Nonnull
  public Maybe<DiscoInfo> queryDiscoInfo(@Nonnull final Jid jid) {
    return getSession().sendIqQuery(
        CommonXmlns.SERVICE_DISCOVERY + "#info",
        jid,
        null
    ).getResponse().map(Stanza::getXml).map(BasePlugin::convertToDiscoInfo);
  }

  /**
   * Queries {@link DiscoItem}s associated with a {@link Jid}.
   */
  @Nonnull
  public Maybe<List<DiscoItem>> queryDiscoItems(@Nonnull final Jid jid,
                                                @Nullable final String node) {
    final Map<String, String> param = new HashMap<>(1);
    param.put("node", node);
    return getSession().sendIqQuery(
        CommonXmlns.SERVICE_DISCOVERY + "#items",
        jid,
        param
    ).getResponse().map(Stanza::getXml).map(BasePlugin::convertToDiscoItems);
  }

  /**
   * Queries information of the XMPP software.
   */
  @Nonnull
  public Maybe<SoftwareInfo> querySoftwareInfo(@Nonnull final Jid jid) {
    return getSession().sendIqQuery(
        CommonXmlns.SOFTWARE_VERSION, jid,
        null
    ).getResponse().map(BasePlugin::convertToSoftwareInfo);
  }

  @Nonnull
  public Maybe<List<RosterItem>> queryRoster() {
    return getSession()
        .sendIqQuery(CommonXmlns.ROSTER, null, null)
        .getResponse()
        .map(Stanza::getXml)
        .map(BasePlugin::convertToRosterItems);
  }

  @Nonnull
  public Maybe<List<RosterItem>>
  queryRoster(@Nonnull final String version,
              @Nonnull final Collection<RosterItem> cached) {
    final Map<String, String> param = new HashMap<>();
    param.put("ver", version);
    throw new UnsupportedOperationException();
  }

  /**
   * Pings an entity. Signals a {@link java.util.NoSuchElementException} if the remote entity never
   * responds before the {@link Session} is disposed of. Signals a
   * {@link chat.viska.xmpp.StreamErrorException} if a stream error is received.
   * @param jid The entity address. If {@code null} is specified, the server will be pinged.
   * @throws IllegalStateException If the plugin is not applied to any {@link Session}.
   */
  @Nonnull
  public Completable ping(@Nullable Jid jid) {
    if (getSession() == null) {
      throw new IllegalStateException();
    }
    return getSession().sendIqQuery(
        CommonXmlns.PING, jid, null
    ).getResponse().toSingle().toCompletable();
  }

  @Override
  @Nonnull
  public Set<String> getFeatures() {
    return Collections.unmodifiableSet(features);
  }

  @Override
  @Nonnull
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return Collections.unmodifiableSet(SUPPORTED_IQS);
  }

  @Override
  public void onApplied(@Nonnull final Session session) {
    this.session = session;

    // Software Version
    session
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SOFTWARE_VERSION))
        .subscribe(it -> session.send(getSoftwareVersionResult(it.getSender(), it.getId())));

    // disco#info
    session
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SERVICE_DISCOVERY + "#info"))
        .subscribe(it -> session.send(getDiscoInfoResult(it.getSender(), it.getId())));

    // disco#items
    session
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(
            CommonXmlns.SERVICE_DISCOVERY + "#items"
        ))
        .subscribe(it -> session.send(getDiscoItemsResult(it)));

    // Ping
    session
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> "query".equals(it.getIqName()))
        .filter(it -> CommonXmlns.PING.equals(it.getIqNamespace()))
        .subscribe(it -> session.send(it.getResultTemplate()));
  }

  @Nullable
  @Override
  public Session getSession() {
    return session;
  }

  @Nonnull
  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    return Collections.emptySet();
  }
}