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
import chat.viska.xmpp.Session;
import chat.viska.xmpp.Stanza;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
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
import org.xml.sax.SAXException;

/**
 * Provides the most fundamental features of an XMPP session.. This plugin is
 * built-in and needs be applied manually.
 *
 * <h1>Supported XMPP Extensions</h1>
 * <ul>
 *   <li><a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software Version</a></li>
 * </ul>
 */
public class BasePlugin extends BlankPlugin {

  private static final Set<String> features = new HashSet<>(Arrays.asList(
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

  @Nullable
  private static List<DiscoItem> convertToDiscoItems(@NonNull final Document xml) {
    final String xmlns = CommonXmlns.SERVICE_DISCOVERY + "#items";
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("No IQ element found.");
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

  @Nullable
  private static DiscoInfo convertToDiscoInfo(@NonNull final Document xml) {
    final String xmlns = CommonXmlns.SERVICE_DISCOVERY + "#info";
    final Element queryElement = (Element) xml
        .getDocumentElement()
        .getElementsByTagNameNS(xmlns, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("No IQ element found.");
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

  @NonNull
  private static SoftwareInfo convertToSoftwareInfo(@NonNull final Stanza stanza) {
    final Element queryElement = (Element) stanza
        .getDocument()
        .getDocumentElement()
        .getElementsByTagNameNS(CommonXmlns.SOFTWARE_VERSION, "query")
        .item(0);
    if (queryElement == null) {
      throw new IllegalArgumentException("No IQ element found.");
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

  private static List<RosterItem> convertToRosterItems(@NonNull final Document xml) {
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

  @NonNull
  private Document getSoftwareVersionResult(@NonNull final Jid recipient,
                                            @NonNull final String id) {
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

  @NonNull
  private Document getDiscoInfoResult(@NonNull final Jid recipient,
                                      @NonNull final String id) {
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

  @NonNull
  private Document getDiscoItemsResult(@NonNull final Stanza query) {
    final Document result = query.getResultTemplate();
    final Element queryElement = result.createElementNS(
        CommonXmlns.SERVICE_DISCOVERY + "#items",
        "query"
    );
    final String node = (
        (Element) query.getDocument().getDocumentElement().getFirstChild()
    ).getAttribute("node");
    if (!node.isEmpty()) {
      queryElement.setAttribute("node", node);
    }
    result.getDocumentElement().appendChild(queryElement);
    return result;
  }

  @NonNull
  public MutableReactiveObject<String> getSoftwareName() {
    return softwareName;
  }

  @NonNull
  public MutableReactiveObject<String> getSoftwareVersion() {
    return softwareVersion;
  }

  @NonNull
  public MutableReactiveObject<String> getOperatingSystem() {
    return operatingSystem;
  }

  /**
   * Gets and sets the software type. Possible values are listed on the
   * <a href="https://xmpp.org/registrar/disco-categories.html#client">XMPP
   * registrar</a>.
   */
  @NonNull
  public MutableReactiveObject<String> getSoftwareType() {
    return softwareType;
  }

  @NonNull
  public Maybe<DiscoInfo> queryDiscoInfo(@NonNull final Jid jid) {
    try {
      return getSession().query(
          CommonXmlns.SERVICE_DISCOVERY + "#info",
          jid,
          null
      ).getResponse().map(Stanza::getDocument).map(BasePlugin::convertToDiscoInfo);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Queries {@link DiscoItem}s associated with a {@link Jid}.
   */
  @NonNull
  public Maybe<List<DiscoItem>> queryDiscoItems(@NonNull final Jid jid,
                                                @Nullable final String node) {
    final Map<String, String> param = new HashMap<>(1);
    param.put("node", node);
    try {
      return getSession().query(
          CommonXmlns.SERVICE_DISCOVERY + "#items",
          jid,
          param
      ).getResponse().map(Stanza::getDocument).map(BasePlugin::convertToDiscoItems);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * Queries information of the XMPP software.
   */
  @NonNull
  public Maybe<SoftwareInfo> querySoftwareInfo(@NonNull final Jid jid) {
    try {
      return getSession().query(
          CommonXmlns.SOFTWARE_VERSION, jid,
          null
      ).getResponse().map(BasePlugin::convertToSoftwareInfo);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NonNull
  public Maybe<List<RosterItem>> queryRoster() {
    try {
      return getSession()
          .query(CommonXmlns.ROSTER, null, null)
          .getResponse()
          .map(Stanza::getDocument)
          .map(BasePlugin::convertToRosterItems);
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  @NonNull
  public Maybe<List<RosterItem>>
  queryRoster(@NonNull final String version,
              @NonNull final Collection<RosterItem> cached) {
    final Map<String, String> param = new HashMap<>();
    param.put("ver", version);
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<String> getFeatures() {
    return Collections.unmodifiableSet(features);
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return Collections.unmodifiableSet(SUPPORTED_IQS);
  }

  @Override
  public void onApplied(Session session) {
    super.onApplied(session);

    // Software Version
    getSession()
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(CommonXmlns.SOFTWARE_VERSION))
        .subscribe(it -> getSession().send(
            getSoftwareVersionResult(it.getSender(), it.getId())
        ));

    // disco#info
    getSession()
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(
            CommonXmlns.SERVICE_DISCOVERY + "#info"
        ))
        .subscribe(it -> getSession().send(
            getDiscoInfoResult(it.getSender(), it.getId())
        ));

    // disco#items
    getSession()
        .getInboundStanzaStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqName().equals("query"))
        .filter(it -> it.getIqNamespace().equals(
            CommonXmlns.SERVICE_DISCOVERY + "#items"
        ))
        .subscribe(it -> getSession().send(
            getDiscoItemsResult(it)
        ));
  }
}