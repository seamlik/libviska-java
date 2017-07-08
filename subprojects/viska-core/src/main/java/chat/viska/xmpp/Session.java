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

import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.security.cert.Certificate;
import java.util.EventObject;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Future;
import javax.net.ssl.SSLSession;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * XMPP session responsible for connecting to an XMPP server, exchanging XMPP
 * stanzas and all other heavy work. This is the main entry point for an XMPP
 * client developer.
 *
 * <p>Different implementations of this interface differ in the connection
 * methods they use and how they are implemented, e.g. WebSocket vs. plain TCP
 * and <a href="https://netty.io">Netty</a> vs. {@link java.nio}.</p>
 *
 * <p>Implementations of this interface must designed as thread-safe.</p>
 *
 * <h1>Usage</h1>
 *
 * <p>The following is a brief workflow of using a {@link Session}:</p>
 *
 * <ol>
 *   <li>Initialize a {@link Session} subclass.</li>
 *   <li>
 *     Obtain a {@link Connection} based on the domain name of an XMPP server.
 *   </li>
 *   <li>
 *     Adjust options like:
 *     <ul>
 *       <li>TLS.</li>
 *       <li>Compression.</li>
 *       <li>Preferred locale list.</li>
 *       <li>Device ID, a.k.a. XMPP resource (not recommended).</li>
 *     </ul>
 *   </li>
 *   <li>
 *     Apply some {@link Plugin}s in order to support more XMPP extensions.
 *     Note that {@link BasePlugin} is a built-in one and need not be applied
 *     manually.
 *   </li>
 *   <li>Login or register using {@code login()} and {@code register()}.</li>
 *   <li>
 *     Shutdown the {@link Session} using {@link #disconnect()}.
 *   </li>
 * </ol>
 */
public interface Session {

  /**
   * Runtime state of a {@link Session}.
   *
   * <h1>State diagram</h1>
   * <pre>{@code
   *                                                      +---------------+
   *                          disconnect()                |               |
   *         +------------------------------------------+ | DISCONNECTED  |    <------------+ <----------------------------+ <---------------------+
   *         |                                            |               |                                                                        |
   *         |                                            +---------------+                 ^                              ^                       |
   *         |                                                                              |                              |                       |
   *         |                                                  +  ^                        |                              |                       |
   *         |                                      reconnect() |  | Connection loss        | Connection loss              | Connection loss       | Connection loss
   *         v                                                  v  +                        +                              +                       +
   *
   * +--------------+    +---------------+                +--------------+           +--------------+               +--------------+        +--------------+
   * |              |    |               |    login()     |              |           |              |               |              |        |              |
   * |   DISPOSED   |    |  INITIALIZED  | +----------->  |  CONNECTING  | +-------> |  CONNECTED   | +---------->  | HANDSHAKING  | +----> |   ONLINE     |
   * |              |    |               |   register()   |              |           |              |               |              |        |              |
   * +--------------+    +---------------+                +--------------+           +--------------+               +--------------+        +--------------+
   *
   *         ^                                                   +                           +                             +                       +
   *         |                                                   | disconnect()              | disconnect()                | disconnect()          | disconnect()
   *         |                                                   | Server disconnects        | Server disconnects          | Server disconnects    | Server disconnects
   *         |                                                   v                           |                             |                       |
   *         |                                                                               v                             v                       |
   *         |                                            +---------------+                                                                        |
   *         +------------------------------------------+ |               | <----------------+ <---------------------------+ <---------------------+
   *                                                      | DISCONNECTING |
   *                                                      |               |
   *                                                      +---------------+
   * }</pre>
   */
  enum State {

    /**
     * Indicates the {@link Session} has just been created.
     */
    INITIALIZED,

    /**
     * Indicates a network connection to the server is established and is
     * about to login or perform in-band registration.
     */
    CONNECTED,

    /**
     * Indicates the {@link Session} is establishing a network
     * connection to a server.
     */
    CONNECTING,

    /**
     * Indicates the network connection is lost and waiting to reconnect and
     * resume the XMPP stream. However, it enters {@link State#DISPOSED} directly
     * upon losing the connection if
     * <a href="https://xmpp.org/extensions/xep-0198.html">Stream
     * Management</a> is disabled.
     */
    DISCONNECTED,

    /**
     * Indicates the {@link Session} is closing the connection or the
     * server is doing so.
     */
    DISCONNECTING,

    /**
     * Indicates the {@link Session} has been shut down. Most actions
     * that changes the state will throw an {@link IllegalStateException}.
     */
    DISPOSED,

    /**
     * Indicates the {@link Session} is logging in.
     */
    HANDSHAKING,

    /**
     * Indicates the user has logged into the server.
     */
    ONLINE
  }

  /**
   * Starts logging in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED}.
   */
  @NonNull
  Future<Void> login(@NonNull Jid jid, @NonNull String password);

  /**
   * Starts logging in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED}.
   */
  @NonNull
  Future<Void> login(@NonNull Jid authnId,
                     @Nullable Jid authzId,
                     @NonNull CredentialRetriever retriever,
                     @Nullable String resource);

  /**
   * Starts an in-band registration and then log in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED}.
   */
  @NonNull
  Future<Void> register(@NonNull Jid jid, @NonNull String password);

  /**
   * Starts an in-band registration and then log in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED}.
   */
  @NonNull
  Future<Void> register(@NonNull Jid authnId,
                        @Nullable Jid authzId,
                        @NonNull CredentialRetriever retriever,
                        @Nullable String resource);

  /**
   * Starts reconnecting and resuming the XMPP stream.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#DISCONNECTED}.
   * @throws UnsupportedOperationException If
   *         <a href="https://xmpp.org/extensions/xep-0198.html"> Stream
   *         Management</a> is disabled.
   */
  @NonNull
  Future<Void> reconnect();

  /**
   * Starts closing the XMPP stream, the network connection and releasing system
   * resources. This method is non-blocking but cancellation is not supported
   * for it is non-recoverable. In order to get informed once the method
   * completes, capture a {@link java.beans.PropertyChangeEvent} with the
   * property name being {@code State} and the new value being
   * {@link State#DISCONNECTED}.
   */
  void disconnect();

  /**
   * Sends an XML stanza to the server. The stanza will not be validated by any
   * means, so beware that the server may close the connection once any policy
   * is violated.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED} or {@link State#DISCONNECTING}.
   */
  void send(@NonNull String xml) throws SAXException;

  /**
   * Sends an XML stanza to the server. The stanza will not be validated by any
   * means, so beware that the server may close the connection once any policy
   * is violated.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED} or {@link State#DISCONNECTING}.
   */
  void send(@NonNull Document xml);

  /**
   * Gets the connection level {@link Compression}.
   * @return {@code null} is no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getConnectionCompression();

  /**
   * Sets the connection level {@link Compression}.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED} or {@link State#DISCONNECTED}.
   * @throws IllegalArgumentException If the specified {@link Compression} is
   *         unsupported.
   */
  void setConnectionCompression(@Nullable Compression method);

  /**
   * Gets the TLS level {@link Compression} which is defined in
   * <a href="https://datatracker.ietf.org/doc/rfc3749">RFC 3749: Transport
   * Layer Security Protocol Compression Methods</a>.
   * @return {@code null} if no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getTlsCompression();

  /**
   * Sets the TLS level {@link Compression} which is defined in
   * <a href="https://datatracker.ietf.org/doc/rfc3749">RFC 3749: Transport
   * Layer Security Protocol Compression Methods</a>.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED} or {@link State#DISCONNECTED}.
   * @throws IllegalArgumentException If the specified {@link Compression} is
   *         unsupported.
   */
  void setTlsCompression(Compression compression);

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   */
  @Nullable
  Compression getStreamCompression();

  /**
   * Sets the stream level {@link Compression} which is standardized in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED} or {@link State#DISCONNECTED}.
   * @throws IllegalArgumentException If the specified {@link Compression} is
   *         unsupported.
   */
  void setStreamCompression(@Nullable Compression streamCompression);

  /**
   * Gets the standard name of the cipher suite used in the TLS connection.
   * @see SSLSession#getCipherSuite()
   * @return An empty {@link String} if TLS is disabled.
   */
  @NonNull
  String getTlsCipherSuite();

  /**
   * Gets the name of the TLS protocol used in the connection.
   * @see SSLSession#getProtocol()
   * @return An empty {@link String} if TLS is disabled.
   */
  @NonNull
  String getTlsProtocol();

  /**
   * Gets the {@link Certificate}s sent to the server during TLS handshaking.
   * @see SSLSession#getLocalCertificates()
   */
  @NonNull
  Certificate[] getTlsLocalCertificates();

  /**
   * Gets the {@link Certificate}s present by the server.
   * @see SSLSession#getPeerCertificates()
   */
  @NonNull
  Certificate[] getTlsPeerCertificates();

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   */
  @Nullable
  Connection getConnection();

  /**
   * Gets a stream of inbound XMPP stanzas. These include {@code <iq/>},
   * {@code <message/>} and {@code <presence/>} only. The stream will not emits
   * any errors but will emit a completion after this class is disposed of.
   */
  @NonNull
  Observable<Document> getInboundStanzaStream();

  /**
   * Gets the logging manager.
   */
  @NonNull
  LoggingManager getLoggingManager();

  /**
   * Gets the plugin manager.
   */
  @NonNull
  PluginManager getPluginManager();

  /**
   * Gets the current {@link State}. When an {@link Session} is first
   * created, the {@link State} is always {@link State#DISCONNECTED}.
   */
  @NonNull
  State getState();

  /**
   * Gets the bare JID logged in this session.
   * @return {@code null} if the handshake has not completed yet.
   */
  @Nullable
  Jid getJid();

  /**
   * Gets a stream of emitted {@link EventObject}s. It never emits any errors
   * but will emit a completion signal after this class enters
   * {@link State#DISPOSED}.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   *   <li>
   *     {@link java.beans.PropertyChangeEvent}
   *     <ul>
   *       <li>{@code State} ({@link State})</li>
   *     </ul>
   *   </li>
   * </ul>
   */
  @NonNull
  Observable<EventObject> getEventStream();

  /**
   * Gets a {@link Set} of XML namespaces representing XMPP extensions currently
   * enabled. This method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return Empty {@link Set} if no features is enabled currently.
   */
  @NonNull
  Set<String> getFeatures();

  /**
   * Gets the preferred {@link Locale}s. Some properties of or
   * results queried from XMPP peers are human-readable texts and therefore may
   * have multiple localized versions. For example, the "category" and "type" of
   * an {@link chat.viska.xmpp.DiscoInfo.Identity} returned by a feature query
   * in <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>. In this case, only the result matched by the preferred
   * {@link Locale}s is returned to the method caller.
   */
  @NonNull
  Locale[] getLocales();

  /**
   * Sets the preferred locales. It can be set even while the session is up and
   * running.
   *
   * <p>By default it is set to the JVM's system {@link Locale} which is a
   * single one. On platforms such as Android Nougat where users can set
   * multiple {@link Locale}s, it is needed to set this property manually.</p>
   */
  void setLocales(Locale... locales);

  @NonNull
  String[] getSaslMechanisms();

  void setSaslMechanisms(String... mechanisms);
}