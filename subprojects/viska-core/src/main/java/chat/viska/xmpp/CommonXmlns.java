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
public final class CommonXmlns {
  public static final String BOSH = "urn:xmpp:alt-connections:xbosh";
  public static final String COMPRESSION = "http://jabber.org/features/compress";
  public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
  public static final String STANZA = "jabber:client";
  public static final String STARTTLS = "urn:ietf:params:xml:ns:xmpp-tls";
  public static final String STREAM = "urn:ietf:params:xml:ns:xmpp-streams";
  public static final String STREAM_HEADER_TCP = "http://etherx.jabber.org/streams";
  public static final String STREAM_HEADER_WEBSOCKET = "urn:ietf:params:xml:ns:xmpp-framing";
  public static final String WEBSOCKET = "urn:xmpp:alt-connections:websocket";
}