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
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Provides the most fundamental features of an XMPP session. This plugin is
 * built-in and needs to be applied manually.
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
  private Session.PluginContext context;

  @Nullable
  private static List<DiscoItem> convertToDiscoItems(@Nonnull final Document xml) {
    final Element queryElement = (Element) xml.getDocumentElement().getFirstChild();
    return Observable.fromIterable(DomUtils.convertToList(
        queryElement.getElementsByTagName("item")
    )).filter(it -> it.getParentNode() == queryElement).cast(Element.class).map(it -> new DiscoItem(
        new Jid(it.getAttribute("jid")),
        it.getAttribute("name"),
        it.getAttribute("node")
    )).toList().blockingGet();
  }

  @Nonnull
  private static DiscoInfo convertToDiscoInfo(@Nonnull final Document xml) {
    final Element queryElement = (Element) xml.getDocumentElement().getFirstChild();
    final List<DiscoInfo.Identity> identities = Observable.fromIterable(
        DomUtils.convertToList(queryElement.getElementsByTagName("identity"))
    ).cast(Element.class).filter(it -> it.getParentNode() == queryElement).map(
        it -> new DiscoInfo.Identity(
            it.getAttribute("category"),
            it.getAttribute("name"),
            it.getAttribute("type")
    )).toList().blockingGet();
    final List<String> features = Observable.fromIterable(
        DomUtils.convertToList(queryElement.getElementsByTagName("feature"))
    ).cast(Element.class).filter(it -> it.getParentNode() == queryElement).map(
        it -> it.getAttribute("var")
    ).toList().blockingGet();
    return new DiscoInfo(identities, features);
  }

  @Nonnull
  private static SoftwareInfo convertToSoftwareInfo(@Nonnull final Stanza stanza) {
    final Element queryElement = (Element) stanza.getXml().getDocumentElement().getFirstChild();
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

  @Nonnull
  private static List<RosterItem> convertToRosterItems(@Nonnull final Document xml) {
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(CommonXmlns.ROSTER, "query")
        .item(0);
    return Observable.fromIterable(
        DomUtils.convertToList(queryElement.getElementsByTagName("item"))
    ).filter(it -> it.getParentNode() == queryElement).cast(Element.class).map(it -> new RosterItem(
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
  private Document getSoftwareVersionResult(@Nonnull final Stanza query) {
    final Document result = query.getResultTemplate();
    final Node queryElement = result.getDocumentElement().appendChild(result.createElementNS(
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
  private Document getDiscoInfoResult(@Nonnull final Stanza query) {
    final Document result = query.getResultTemplate();
    final Node queryElement = result.getDocumentElement().appendChild(result.createElementNS(
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
        getSession().getPluginManager().getAllFeatures()
    ).subscribe(it -> {
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
    return this.context.sendIq(
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
    final Map<String, String> param;
    if (StringUtils.isBlank(node)) {
      param = null;
    } else {
      param = new HashMap<>(1);
      param.put("node", node);
    }

    return this.context.sendIq(
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
    return this.context.sendIq(
        CommonXmlns.SOFTWARE_VERSION, jid,
        null
    ).getResponse().map(BasePlugin::convertToSoftwareInfo);
  }

  @Nonnull
  public Maybe<List<RosterItem>> queryRoster() {
    return this
        .context
        .sendIq(CommonXmlns.ROSTER, null, null)
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
    final Document iq = Stanza.getIqTemplate(
        Stanza.IqType.GET,
        UUID.randomUUID().toString(),
        this.context.getSession().getNegotiatedJid(),
        jid
    );
    iq.getDocumentElement().appendChild(iq.createElementNS(CommonXmlns.PING, "ping"));
    return this.context.sendIq(new Stanza(iq)).getResponse().toSingle().toCompletable();
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
  public void onApplying(@Nonnull final Session.PluginContext context) {
    this.context = context;

    // Software Version
    context
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SOFTWARE_VERSION))
        .subscribe(it -> context.sendIq(new Stanza(getSoftwareVersionResult(it))));

    // disco#info
    context
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SERVICE_DISCOVERY + "#info"))
        .subscribe(it -> context.sendIq(new Stanza(getDiscoInfoResult(it))));

    // disco#items
    context
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SERVICE_DISCOVERY + "#items"))
        .subscribe(it -> context.sendIq(new Stanza(getDiscoItemsResult(it))));

    // Ping
    context
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> "ping".equals(it.getIqName()))
        .filter(it -> CommonXmlns.PING.equals(it.getIqNamespace()))
        .subscribe(it -> context.sendIq(new Stanza(it.getResultTemplate())));
  }

  @Nonnull
  @Override
  public Session getSession() {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context.getSession();
  }

  @Nonnull
  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    return Collections.emptySet();
  }
}