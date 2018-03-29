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

package chat.viska.xmpp.plugins.base;

import chat.viska.commons.DomUtils;
import chat.viska.commons.EnumUtils;
import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.IqSignature;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.Stanza;
import chat.viska.xmpp.StreamErrorException;
import chat.viska.xmpp.XmlWrapperStanza;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import rxbeans.MutableProperty;
import rxbeans.StandardProperty;

/**
 * Provides the most fundamental features of an XMPP session. This plugin is
 * built-in and needs to be applied manually.
 *
 * <p>Supported XMPP Extensions are:</p>
 * <ul>
 *   <li><a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software Version</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0199.html">XEP-0199: XMPP Ping</a></li>
 * </ul>
 */
@Plugin.Features({
    CommonXmlns.PING,
    CommonXmlns.SERVICE_DISCOVERY + "#info",
    CommonXmlns.SERVICE_DISCOVERY + "#items",
    CommonXmlns.SOFTWARE_VERSION
})
public class BasePlugin implements Plugin {

  private static final Set<IqSignature> SUPPORTED_IQS = new HashSet<>(Arrays.asList(
      new IqSignature(
          CommonXmlns.SERVICE_DISCOVERY + "#info", "query"
      ),
      new IqSignature(
          CommonXmlns.SERVICE_DISCOVERY + "#items", "query"
      ),
      new IqSignature(
          CommonXmlns.SOFTWARE_VERSION, "query"
      ),
      new IqSignature(CommonXmlns.ROSTER, "query")
  ));

  private final MutableProperty<String> softwareName = new StandardProperty<>("");
  private final MutableProperty<String> softwareVersion = new StandardProperty<>("");
  private final MutableProperty<String> operatingSystem = new StandardProperty<>("");
  private final MutableProperty<String> identityType = new StandardProperty<>("");
  private final MutableProperty<String> identityCategory = new StandardProperty<>("client");
  private Session.@MonotonicNonNull PluginContext context;

  private static List<DiscoItem> convertToDiscoItems(final Stanza stanza)
      throws StreamErrorException {
    final Element queryElement = stanza.getIqElement();
    return DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .map(it -> new DiscoItem(
            new Jid(it.getAttribute("jid")),
            it.getAttribute("name"),
            it.getAttribute("node")
        ))
        .collect(Collectors.toList());
  }

  private static DiscoInfo convertToDiscoInfo(final Stanza stanza) throws StreamErrorException {
    final Element queryElement = stanza.getIqElement();
    final List<DiscoInfo.Identity> identities = DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "identity".equals(it.getLocalName()))
        .filter(it -> (CommonXmlns.SERVICE_DISCOVERY + "#info").equals(it.getNamespaceURI()))
        .map(it -> new DiscoInfo.Identity(
            it.getAttribute("category"),
            it.getAttribute("name"),
            it.getAttribute("type")
        ))
        .collect(Collectors.toList());
    final List<String> features = DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "feature".equals(it.getLocalName()))
        .filter(it -> (CommonXmlns.SERVICE_DISCOVERY + "#info").equals(it.getNamespaceURI()))
        .map(it -> it.getAttribute("var"))
        .collect(Collectors.toList());
    return new DiscoInfo(identities, features);
  }

  private static SoftwareInfo convertToSoftwareInfo(final Stanza stanza) throws StreamErrorException {
    final Element queryElement = stanza.getIqElement();
    final String name = DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "name".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.SOFTWARE_VERSION.equals(it.getNamespaceURI()))
        .map(Node::getTextContent)
        .findFirst()
        .orElse("");
    final String version = DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "version".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.SOFTWARE_VERSION.equals(it.getNamespaceURI()))
        .map(Node::getTextContent)
        .findFirst()
        .orElse("");
    final String os = DomUtils
        .convertToList(queryElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "os".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.SOFTWARE_VERSION.equals(it.getNamespaceURI()))
        .map(Node::getTextContent)
        .findFirst()
        .orElse("");
    return new SoftwareInfo(name, version, os);
  }

  private static List<RosterItem> convertToRosterItems(final Document xml)
      throws StreamErrorException {
    final Optional<Element> queryElement = DomUtils
        .convertToList(xml.getDocumentElement().getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "query".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.ROSTER.equals(it.getNamespaceURI()))
        .findFirst();
    if (!queryElement.isPresent()) {
      throw new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Malformed roster query result."
      );
    }
    return DomUtils
        .convertToList(queryElement.get().getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "item".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.ROSTER.equals(it.getNamespaceURI()))
        .map(it -> {
          final Collection<String> groups = DomUtils
              .convertToList(it.getChildNodes())
              .parallelStream()
              .map(Node::getTextContent)
              .collect(Collectors.toList());
          return new RosterItem(
              new Jid(it.getAttribute("jid")),
              EnumUtils.fromXmlValue(
                  RosterItem.Subscription.class,
                  it.getAttribute("subscription")
              ),
              it.getAttribute("name"),
              groups
          );
        })
        .collect(Collectors.toList());
  }

  private Document generateSoftwareVersionResult(final Stanza query) {
    final Document result = XmlWrapperStanza.createIqResult(query);
    final Node queryElement = result.getDocumentElement().appendChild(result.createElementNS(
        CommonXmlns.SOFTWARE_VERSION,
        "query"
    ));
    queryElement
        .appendChild(result.createElement("name"))
        .setTextContent(softwareName.get());
    queryElement
        .appendChild(result.createElement("version"))
        .setTextContent(softwareVersion.get());
    queryElement
        .appendChild(result.createElement("os"))
        .setTextContent(operatingSystem.get());
    return result;
  }

  private Document generateDiscoInfoResult(final Stanza query) {
    if (context == null) {
      throw new IllegalStateException();
    }

    final Document result = XmlWrapperStanza.createIqResult(query);
    final Node queryElement = result.getDocumentElement().appendChild(result.createElementNS(
        CommonXmlns.SERVICE_DISCOVERY + "#info",
        "query"
    ));
    final Element identityElement = (Element) queryElement.appendChild(
        result.createElement("identity")
    );
    identityElement.setAttribute("category", "client");
    identityElement.setAttribute("type", identityType.get());
    identityElement.setAttribute("name", softwareName.get());
    context.getSession().getPluginManager().getAllFeatures().stream().sorted().forEach(it -> {
      final Element featureElement = (Element) queryElement.appendChild(
          result.createElement("feature")
      );
      featureElement.setAttribute("var", it);
    });
    return result;
  }

  private Document generateDiscoItemsResult(final Stanza query) {
    // TODO: Figure out what's happening here
    final Document result = XmlWrapperStanza.createIqResult(query);
    final Element queryElement = result.createElementNS(
        CommonXmlns.SERVICE_DISCOVERY + "#items",
        "query"
    );
    final String node = (
        (Element) query.toXml().getDocumentElement().getFirstChild()
    ).getAttribute("node");
    if (!node.isEmpty()) {
      queryElement.setAttribute("node", node);
    }
    result.getDocumentElement().appendChild(queryElement);
    return result;
  }

  /**
   * Software name.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>
   */
  public MutableProperty<String> softwareNameProperty() {
    return softwareName;
  }

  /**
   * Software version.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>
   */
  public MutableProperty<String> softwareVersionProperty() {
    return softwareVersion;
  }

  /**
   * Operating system name.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>
   */
  public MutableProperty<String> operatingSystemProperty() {
    return operatingSystem;
  }

  /**
   * Type of the identity. Possible values are listed on the
   * <a href="https://xmpp.org/registrar/disco-categories.html#client">XMPP
   * registrar</a>.
   */
  public MutableProperty<String> identityTypeProperty() {
    return identityType;
  }

  /**
   * Category of the identity. Possible values are listed on the
   * <a href="https://xmpp.org/registrar/disco-categories.html">XMPP
   * registrar</a>.
   *
   * <p>Default: {@code client}</p>
   */
  public MutableProperty<String> identityCategoryProperty() {
    return identityCategory;
  }

  /**
   * Queries {@link DiscoItem}s associated with a {@link Jid}.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a>
   */
  public Maybe<DiscoInfo> queryDiscoInfo(final Jid jid) {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context.sendIq(
        CommonXmlns.SERVICE_DISCOVERY + "#info",
        jid,
        Collections.emptyMap()
    ).getResponse().map(BasePlugin::convertToDiscoInfo);
  }

  /**
   * Queries {@link DiscoItem}s associated with a {@link Jid}.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a>
   */
  public Maybe<List<DiscoItem>> queryDiscoItems(final Jid jid, final String node) {
    if (context == null) {
      throw new IllegalStateException();
    }
    final Map<String, String> param = new HashMap<>();
    if (!node.isEmpty()) {
      param.put("node", node);
    }
    return context.sendIq(
        CommonXmlns.SERVICE_DISCOVERY + "#items",
        jid,
        param
    ).getResponse().map(BasePlugin::convertToDiscoItems).doOnError(it -> {
      if (it instanceof StreamErrorException) {
        context.sendError((StreamErrorException) it);
      }
    });
  }

  /**
   * Queries information of the XMPP software.
   */
  public Maybe<SoftwareInfo> querySoftwareInfo(final Jid jid) {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context.sendIq(
        CommonXmlns.SOFTWARE_VERSION, jid,
        Collections.emptyMap()
    ).getResponse().map(BasePlugin::convertToSoftwareInfo).doOnError(it -> {
      if (it instanceof StreamErrorException) {
        context.sendError((StreamErrorException) it);
      }
    });
  }

  /**
   * Queries roster.
   */
  public Maybe<List<RosterItem>> queryRoster() {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context
        .sendIq(CommonXmlns.ROSTER, Jid.EMPTY, Collections.emptyMap())
        .getResponse()
        .map(Stanza::toXml)
        .map(BasePlugin::convertToRosterItems)
        .doOnError(it -> {
          if (it instanceof StreamErrorException) {
            context.sendError((StreamErrorException) it);
          }
        });
  }

  /**
   * Quries roster.
   */
  public Maybe<List<RosterItem>> queryRoster(final String version,
                                             final Collection<RosterItem> cached) {
    final Map<String, String> param = new HashMap<>();
    param.put("ver", version);
    throw new UnsupportedOperationException();
  }

  /**
   * Pings an entity. Signals a {@link java.util.NoSuchElementException} if the remote entity never
   * responds before the {@link Session} is disposed of.
   * @param jid The entity address. If empty, the server will be pinged.
   * @see <a href="https://xmpp.org/extensions/xep-0199.html">XEP-0199: XMPP Ping</a>
   */
  public Completable ping(final Jid jid) {
    if (context == null) {
      throw new IllegalStateException();
    }
    final Document iq = XmlWrapperStanza.createIq(
        Stanza.IqType.GET,
        UUID.randomUUID().toString(),
        this.context.getSession().getNegotiatedJid(),
        jid
    );
    iq.getDocumentElement().appendChild(iq.createElementNS(CommonXmlns.PING, "ping"));
    return this.context.sendIq(new XmlWrapperStanza(iq)).getResponse().toSingle().toCompletable();
  }

  @Override
  public Set<IqSignature> getSupportedIqs() {
    return Collections.unmodifiableSet(SUPPORTED_IQS);
  }

  @Override
  public void onApplying(final Session.PluginContext context) {
    this.context = context;

    // Software Version
    context
        .getInboundIqStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqSignature().equals(CommonXmlns.SOFTWARE_VERSION, "query"))
        .subscribe(it -> context.sendIq(new XmlWrapperStanza(generateSoftwareVersionResult(it))));

    // disco#info
    context
        .getInboundIqStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqSignature().equals(CommonXmlns.SERVICE_DISCOVERY + "#info", "query"))
        .subscribe(it -> context.sendIq(new XmlWrapperStanza(generateDiscoInfoResult(it))));

    // disco#items
    context
        .getInboundIqStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqSignature().equals(CommonXmlns.SERVICE_DISCOVERY + "#items", "query"))
        .subscribe(it -> context.sendIq(new XmlWrapperStanza(generateDiscoItemsResult(it))));

    // Ping
    context
        .getInboundIqStream()
        .filter(it -> it.getIqType() == Stanza.IqType.GET)
        .filter(it -> it.getIqSignature().equals(CommonXmlns.PING, "ping"))
        .subscribe(it -> context.sendIq(new XmlWrapperStanza(XmlWrapperStanza.createIqResult(it))));
  }
}