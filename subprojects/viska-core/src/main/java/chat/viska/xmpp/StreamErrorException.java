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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Indicates a stream error has occurred or is received.
 */
public class StreamErrorException extends Exception {

  /**
   * Cause of a stream error. These conditions are defined in
   * <a href="https://tools.ietf.org/html/rfc6120#section-4.9.3">RFC 6120 -
   * Extensible Messaging and Presence Protocol (XMPP): Core</a>.
   */
  public enum Condition {

    BAD_FORMAT,
    BAD_NAMESPACE_PREFIX,
    CONFLICT,
    CONNECTION_TIMEOUT,
    HOST_GONE,
    HOST_UNKNOWN,
    IMPROPER_ADDRESSING,
    INTERNAL_SERVER_ERROR,
    INVALID_FROM,
    INVALID_NAMESPACE,
    INVALID_XML,
    NOT_AUTHORIZED,
    NOT_WELL_FORMED,
    POLICY_VIOLATION,
    REMOTE_CONNECTION_FAILED,
    RESET,
    RESOURCE_CONSTRAINT,
    RESTRICTED_XML,
    SEE_OTHER_HOST,
    SYSTEM_SHUTDOWN,
    UNDEFINED_CONDITION,
    UNSUPPORTED_ENCODING,
    UNSUPPORTED_FEATURE,
    UNSUPPORTED_STANZA_TYPE,
    UNSUPPORTED_VERSION;
  }

  private final Condition condition;
  private final String text;

  public static StreamErrorException fromXml(@NonNull final Document xml) {
    StreamErrorException.Condition condition = null;
    Element conditionElement = null;
    int cursor = 0;
    final NodeList nodes = xml.getDocumentElement().getChildNodes();
    while (cursor < nodes.getLength()) {
      conditionElement = (Element) nodes.item(cursor);
      condition = EnumUtils.fromXmlValue(
          StreamErrorException.Condition.class,
          conditionElement.getLocalName()
      );
      if (condition != null) {
        break;
      } else {
        ++cursor;
      }
    }
    if (condition != null) {
      NodeList textNodes = conditionElement.getElementsByTagNameNS(
          CommonXmlns.STREAM_ERROR, "text"
      );
      if (textNodes.getLength() > 0) {
        return new StreamErrorException(
            condition,
            textNodes.item(0).getTextContent()
        );
      } else {
        return new StreamErrorException(condition);
      }
    } else if (xml.getDocumentElement().hasChildNodes()) {
      return new StreamErrorException(
          StreamErrorException.Condition.UNDEFINED_CONDITION,
          String.format(
              "[UNRECOGNIZED ERROR: %1s]",
              xml.getDocumentElement().getFirstChild().getLocalName()
          )
      );
    } else {
      return new StreamErrorException(
          StreamErrorException.Condition.UNDEFINED_CONDITION,
          "[NO ERROR CONDITION SPECIFIED]"
      );
    }
  }

  public StreamErrorException(@NonNull final Condition condition) {
    super("[" + EnumUtils.toXmlValue(condition) + "]");
    this.text = "";
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public StreamErrorException(@NonNull final Condition condition,
                              @NonNull final String text) {
    super("[" + EnumUtils.toXmlValue(condition) + "] " + text);
    Objects.requireNonNull(condition, "`condition` is absent.");
    Objects.requireNonNull(text, "`text` is absent.");
    this.condition = condition;
    this.text = text;
  }

  @NonNull
  public Condition getCondition() {
    return condition;
  }

  @NonNull
  public String getText() {
    return text;
  }

  @NonNull
  public Document toXml() {
    final Document xml;
    try {
      xml = DomUtils.readDocument(String.format(
          "<error xmlns=\"%1s\"><%2s xmlns=\"%3s\"/></error>",
          CommonXmlns.STREAM_HEADER,
          EnumUtils.toXmlValue(getCondition()),
          CommonXmlns.STREAM_ERROR
      ));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
    if (!getText().isEmpty()) {
      final Element textElement = (Element) xml
          .getDocumentElement()
          .appendChild(xml.createElementNS(CommonXmlns.STREAM_ERROR, "text"));
      textElement.setTextContent(getText());
    }
    return xml;
  }
}