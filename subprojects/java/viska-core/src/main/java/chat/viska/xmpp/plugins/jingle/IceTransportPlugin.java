/*
 * Copyright 2018 Kai-Chung Yan (殷啟聰)
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

package chat.viska.xmpp.plugins.jingle;

import chat.viska.commons.DomUtils;
import chat.viska.commons.XmlTagSignature;
import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.Stanza;
import chat.viska.xmpp.StanzaErrorException;
import chat.viska.xmpp.StreamErrorException;
import chat.viska.xmpp.XmlWrapperStanza;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import rxbeans.StandardObject;

/**
 * Provides support for <a href="https://xmpp.org/extensions/xep-0371.html">Jingle ICE Transport
 * Method</a>.
 */
@Plugin.DependsOn(JinglePlugin.class)
@Plugin.Features(CommonXmlns.JINGLE_ICE)
public class IceTransportPlugin extends StandardObject implements TransportPlugin {

  /**
   * Indicates an {@link IceTransport} is received via a {@code transport-info} Jingle message.
   */
  public class TransportReceivedEvent extends EventObject {

    private final IceTransport transport;
    private final String sessionId;
    private final String contentName;

    /**
     * Default constructor.
     */
    public TransportReceivedEvent(final String sessionId,
                                  final String contentName,
                                  final IceTransport transport) {
      super(IceTransportPlugin.this);
      this.sessionId = sessionId;
      this.contentName = contentName;
      this.transport = transport;
    }

    /**
     * Gets the transport.
     */
    public IceTransport getTransport() {
      return transport;
    }

    /**
     * Gets the content name.
     */
    public String getContentName() {
      return contentName;
    }

    /**
     * Gets the session ID.
     */
    public String getSessionId() {
      return sessionId;
    }
  }

  private Session.@MonotonicNonNull PluginContext context;

  private Session.PluginContext getContext() {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context;
  }

  private Optional<Element> extractTransportFromContent(final Node contentElement) {
    final XmlTagSignature transportSignature = new XmlTagSignature(CommonXmlns.JINGLE_ICE, "transport");
    return DomUtils
        .convertToList(contentElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> transportSignature.equals(it.getNamespaceURI(), it.getLocalName()))
        .findFirst();
  }

  private boolean validateTransportInfoStanza(final Element jingleElement) {
    if (!"transport-info".equals(jingleElement.getAttribute("action"))) {
      return false;
    }

    final XmlTagSignature contentSignature = new XmlTagSignature(CommonXmlns.JINGLE, "content");
    return DomUtils
        .convertToList(jingleElement.getChildNodes())
        .parallelStream()
        .filter(it -> contentSignature.equals(it.getNamespaceURI(), it.getLocalName()))
        .allMatch(it -> extractTransportFromContent(it).isPresent());
  }

  private void processTransportInfoStanza(final Stanza stanza) throws StreamErrorException {
    final String sessionId = stanza.getIqElement().getAttribute("sid");
    if (sessionId.isEmpty()) {
      final Document error = XmlWrapperStanza.createIqError(
          stanza,
          StanzaErrorException.Condition.ITEM_NOT_FOUND,
          StanzaErrorException.Type.MODIFY,
          ""
      );
      error.getDocumentElement().getFirstChild().appendChild(
          error.createElementNS(CommonXmlns.JINGLE_ERRORS, "unknown-session")
      );
      getContext().sendStanza(new XmlWrapperStanza(error));
    }

    DomUtils
        .convertToList(stanza.getIqElement().getChildNodes())
        .parallelStream()
        .map(it -> (Element) it)
        .forEach(content -> {
          final Optional<Element> transportElement = extractTransportFromContent(content);
          if (!transportElement.isPresent()) {
            return;
          }
          triggerEvent(new TransportReceivedEvent(
              sessionId,
              content.getAttribute("name"),
              readTransport(transportElement.get()))
          );
        });
  }

  @Override
  public IceTransport readTransport(final Element transportElement) {
    if (!CommonXmlns.JINGLE_ICE.equals(transportElement.getNamespaceURI())) {
      throw new IllegalArgumentException("Incompatible transport namespace.");
    }

    final String pwd = transportElement.getAttribute("pwd");
    final String ufrag = transportElement.getAttribute("ufrag");
    final List<IceTransport.Candidate> candidates = DomUtils
        .convertToList(transportElement.getChildNodes())
        .stream()
        .map(it -> (Element) it)
        .filter(it -> "candidate".equals(it.getLocalName()))
        .filter(it -> CommonXmlns.JINGLE_ICE.equals(it.getNamespaceURI()))
        .map(it -> {
          final String component = it.getAttribute("component");
          final String generation = it.getAttribute("generation");
          final String port = it.getAttribute("port");
          final String priority = it.getAttribute("priority");
          final String relPort = it.getAttribute("rel-port");
          return new IceTransport.Candidate(
              component.isEmpty() ? (short) -1 : Short.parseShort(component),
              it.getAttribute("foundation"),
              generation.isEmpty() ? -1 : Integer.parseUnsignedInt(generation),
              it.getAttribute("id"),
              it.getAttribute("ip"),
              port.isEmpty() ? -1 : Integer.parseUnsignedInt(port),
              priority.isEmpty() ? -1L : Long.parseUnsignedLong(priority),
              it.getAttribute("protocol"),
              it.getAttribute("rel-addr"),
              relPort.isEmpty() ? -1 : Integer.parseUnsignedInt(relPort),
              it.getAttribute("tcptype"),
              it.getAttribute("type")
          );
        })
        .collect(Collectors.toList());

    return new IceTransport(ufrag, pwd, candidates);
  }

  @Override
  public void writeTransport(final Transport transport, final Element transportElement) {
    final IceTransport iceTransport;
    if (transport instanceof IceTransport) {
      iceTransport = (IceTransport) transport;
    } else {
      throw new IllegalArgumentException("Incompatible transport.");
    }

    if (!iceTransport.getPwd().isEmpty()) {
      transportElement.setAttribute("pwd", iceTransport.getPwd());
    }
    if (!iceTransport.getUfrag().isEmpty()) {
      transportElement.setAttribute("ufrag", iceTransport.getUfrag());
    }
    iceTransport.getCandidates().forEach(it -> {
      final Element element = transportElement.getOwnerDocument().createElement("candidate");
      transportElement.appendChild(element);
      if (it.component >= 0) {
        element.setAttribute("component", Short.toString(it.component));
      }
      if (!it.foundation.isEmpty()) {
        element.setAttribute("foundation", it.foundation);
      }
      if (it.generation >= 0) {
        element.setAttribute("generation", Integer.toString(it.generation));
      }
      if (!it.id.isEmpty()) {
        element.setAttribute("id", it.id);
      }
      if (!it.ip.isEmpty()) {
        element.setAttribute("ip", it.ip);
      }
      if (it.port >= 0) {
        element.setAttribute("port", Integer.toString(it.port));
      }
      if (it.priority >= 0) {
        element.setAttribute("priority", Long.toString(it.priority));
      }
      if (!it.protocol.isEmpty()) {
        element.setAttribute("protocol", it.protocol);
      }
      if (!it.relAddr.isEmpty()) {
        element.setAttribute("rel-addr", it.relAddr);
      }
      if (it.relPort >= 0) {
        element.setAttribute("rel-port", Integer.toString(it.relPort));
      }
      if (!it.tcptype.isEmpty()) {
        element.setAttribute("tcptype", it.tcptype);
      }
      if (!it.type.isEmpty()) {
        element.setAttribute("type", it.type);
      }
    });
  }

  @Override
  public Set<XmlTagSignature> getSupportedIqs() {
    return Collections.singleton(JinglePlugin.JINGLE_SIGNATURE);
  }

  @Override
  public void onApply(final Session.PluginContext context) {
    context
        .getInboundIqStream()
        .filter(it -> validateTransportInfoStanza(it.getIqElement()))
        .subscribe(this::processTransportInfoStanza, context.getRxErrorHandler());
  }
}