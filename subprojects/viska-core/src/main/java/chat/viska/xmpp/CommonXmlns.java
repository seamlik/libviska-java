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

/**
 * Contains {@link String} constants of common XML namespaces.
 */
final class CommonXmlns {
  public static final String BOSH = "urn:xmpp:alt-connections:xbosh";
  public static final String RESOURCE_BINDING = "urn:ietf:params:xml:ns:xmpp-bind";
  public static final String RESOURCE_BINDING_2 = "urn:xmpp:bind2:0";
  public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
  public static final String STANZA_CLIENT = "jabber:client";
  public static final String STANZA_SERVER = "jabber:server";
  public static final String STARTTLS = "urn:ietf:params:xml:ns:xmpp-tls";
  public static final String STREAM_COMPRESSION = "http://jabber.org/features/compress";
  public static final String STREAM_CONTENT = "urn:ietf:params:xml:ns:xmpp-streams";
  public static final String STREAM_HEADER = "http://etherx.jabber.org/streams";
  public static final String STREAM_MANAGEMENT = "urn:xmpp:sm:3";
  public static final String STREAM_OPENING_WEBSOCKET = "urn:ietf:params:xml:ns:xmpp-framing";
  public static final String XEP_SERVICE_DISCOVERY = "http://jabber.org/protocol/disco";
  public static final String WEBSOCKET = "urn:xmpp:alt-connections:websocket";
}