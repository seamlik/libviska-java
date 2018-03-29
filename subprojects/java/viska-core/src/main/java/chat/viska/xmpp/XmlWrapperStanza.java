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
import chat.viska.commons.EnumUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Raw XML data wrapper.
 */
public class XmlWrapperStanza implements Stanza {

  private final Document xml;

  /**
   * Generates a template for an {@code <iq/>}.
   */
  public static Document createIq(final IqType type,
                                  final String id,
                                  final Jid sender,
                                  final Jid recipient) {
    final String iq = String.format(
        "<iq type=\"%1s\" id=\"%2s\"></iq>",
        EnumUtils.toXmlValue(type),
        id
    );
    final Document xml;
    try {
      xml = DomUtils.readDocument(iq);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    if (!Jid.isEmpty(sender)) {
      xml.getDocumentElement().setAttribute("from", sender.toString());
    }
    if (!Jid.isEmpty(recipient)) {
      xml.getDocumentElement().setAttribute("to", recipient.toString());
    }
    return xml;
  }

  /**
   * Generates a template of a result to an {@code <iq/>}.
   */
  public static Document createIqResult(final Stanza stanza) {
    return XmlWrapperStanza.createIq(
        IqType.RESULT,
        stanza.getId(),
        stanza.getRecipient(),
        stanza.getSender()
    );
  }

  /**
   * Generates a template of an {@code <iq/>} error.
   */
  public static Document createIqError(final Stanza stanza,
                                       final StanzaErrorException.Condition condition,
                                       final StanzaErrorException.Type type,
                                       final String msg) {
    final Document xml = createIq(
        IqType.ERROR,
        stanza.getId(),
        stanza.getRecipient(),
        stanza.getSender()
    );
    final Element error = xml.createElement("error");
    error.setAttribute("type", EnumUtils.toXmlValue(type));
    xml.getDocumentElement().appendChild(error);

    error.appendChild(
        xml.createElementNS(CommonXmlns.STANZA_ERROR, EnumUtils.toXmlValue(condition))
    );

    if (!msg.isEmpty()) {
      final Element text = xml.createElementNS(CommonXmlns.STANZA_ERROR, "text");
      text.setTextContent(msg);
      error.appendChild(text);
    }

    return xml;
  }

  /**
   * Default constructor.
   */
  public XmlWrapperStanza(final Document xml) {
    this.xml = xml;
  }

  @Override
  public Document toXml() {
    return xml;
  }
}