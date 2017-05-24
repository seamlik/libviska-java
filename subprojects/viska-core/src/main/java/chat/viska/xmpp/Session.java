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

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.security.cert.Certificate;
import java.util.EventObject;
import java.util.List;
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
 *   <li>Initialize a {@link Session} implementation.</li>
 *   <li>
 *     Obtain a {@link Connection} based on the domain name of an XMPP server.
 *   </li>
 *   <li>
 *     Adjust options like:
 *     <ul>
 *       <li>TLS.</li>
 *       <li>Compression.</li>
 *       <li>
 *         Preferred locale list. Systems like Android Nougat support locale
 *         list natively instead of setting a single locale, which is not
 *         supported by the Java platform. Therefore, the locale list of an
 *         {@link Session} only contains a single default {@link Locale}
 *         can needs to be manually set to match the system settings.
 *       </li>
 *       <li>Device ID, a.k.a. XMPP resource (not recommended).</li>
 *     </ul>
 *   </li>
 *   <li>
 *     Apply some {@link Plugin}s using
 *     {@link #getPluginManager()} in order to support more XMPP
 *     extensions like Jingle VoIP calls, IoT and OMEMO encrypted messaging.
 *     Note that {@link BasePlugin} is a built-in one and need not to be applied
 *     manually.
 *   </li>
 *   <li>
 *     Connect to the server using {@link #connect(Connection)}.
 *   </li>
 *   <li>Login using {@link #login(String, String)}.</li>
 *   <li>
 *     Possibly close the connection using {@link #disconnect()}
 *     and re-adjust some options.
 *   </li>
 *   <li>
 *     Shutdown the {@link Session} using
 *     {@link #dispose()}.
 *   </li>
 * </ol>
 */
public interface Session {

  /**
   * State of a {@link Session}.
   */
  enum State {

    /**
     * Indicates a network connection to the server is established and is
     * waiting to login or perform in-band registration.
     */
    CONNECTED,

    /**
     * Indicates the {@link Session} is establishing a network
     * connection to a server.
     */
    CONNECTING,

    /**
     * Indicates the {@link Session} is currently not connected to any
     * server either because it has just been created or a precious connection
     * was closed.
     */
    DISCONNECTED,

    /**
     * Indicates the {@link Session} is closing the connection or the
     * server is doing so.
     */
    DISCONNECTING,

    /**
     * Indicates the {@link Session} has been disposed of. Any actions
     * that changes the {@link Session}'s {@link State}, e.g. starting
     * another connection, will throw an {@link IllegalStateException}.
     */
    DISPOSED,

    /**
     * Indicates the {@link Session} is handshaking with the server
     * and/or logging in.
     */
    HANDSHAKING,

    /**
     * Indicates the user is online.
     */
    ONLINE
  }

  /**
   * Starts connecting to a server.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   * @throws ConnectionException If the connection fails to be established.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  @NonNull
  Future<Void> connect(@NonNull Connection connection) throws ConnectionException;

  /**
   * Starts logging in the server.
   * @param username The local part of a {@link Jid}. It needs not to be
   *                 {@code stringprep}ed first.
   * @param password The password. It needs not to be {@code stringprep}ed first.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  Future<Void> login(@NonNull String username, @NonNull String password);

  /**
   * Starts closing the connection. This method is non-blocking but cancellation
   * is not supported. In order to get informed when the method completes,
   * capture a {@link java.beans.PropertyChangeEvent} with a property name of
   * {@code State} and a new value of {@link State#DISCONNECTED}.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void disconnect();

  void dispose();

  /**
   * Send an XML to the server. The XML needs even not be an XMPP stanza, so be
   * wary that the server will close the connection once the sent XML violates
   * any XMPP rules.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void send(@NonNull String xml) throws SAXException;

  /**
   * Sends a stream error if receiving any stanzas that violates XMPP rules.
   * Since stream errors are unrecoverable, the connection will be closed
   * immediately after they are sent.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void sendStreamError();

  /**
   * Gets the connection level {@link Compression}.
   * @return {@code null} is no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getConnectionCompression();

  @Nullable
  Compression getBestConnectionCompression();

  /**
   * Sets the connection level {@link Compression}.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void setConnectionCompression(@Nullable Compression method);

  /**
   * Gets the TLS level {@link Compression} which is defined in
   * <a href="https://datatracker.ietf.org/doc/rfc3749">RFC 3749: Transport
   * Layer Security Protocol Compression Methods</a>. Implementations may choose
   * not to implement/enable TLS compression due to security flaws or other
   * reasons.
   * @return {@code null} if no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getTlsCompression();

  @Nullable
  Compression getBestTlsCompression();

  /**
   * Sets the TLS level {@link Compression} which is defined in
   * <a href="https://datatracker.ietf.org/doc/rfc3749">RFC 3749: Transport
   * Layer Security Protocol Compression Methods</a>.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void setTlsCompression(Compression compression);

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   */
  @Nullable
  Compression getStreamCompression();

  @Nullable
  Compression getBestStreamCompression();

  /**
   * Sets the stream level {@link Compression} which is standardized in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   * @throws IllegalArgumentException If the specified {@link Compression} is unsupported.
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
   * Gets a {@link List} of {@link Certificate}s sent to the server during TLS
   * handshaking.
   * @see SSLSession#getLocalCertificates()
   * @return An empty array if TLS is disabled or no certificates are sent.
   */
  @NonNull
  List<Certificate> getTlsLocalCertificates();

  /**
   * Gets a {@link List} of {@link Certificate}s present by the server.
   * @see SSLSession#getPeerCertificates()
   * @return An empty array if TLS is disabled or a non-certificate-based cipher
   * suite such as Kerberos is used.
   */
  @NonNull
  List<Certificate> getTlsPeerCertificates();

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   * @return {@code null} if it is not set yet.
   */
  @Nullable
  Connection getConnection();

  /**
   * Sets the {@link Connection} to be used.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  void setConnection(@NonNull Connection connection);

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
   * Gets the current {@link State}. When an {@link Session} is just
   * created, the {@link State} is always {@link State#DISCONNECTED}.
   * <p>The following figure shows the {@link State} diagram:</p>
   * <pre>
   *   +--------------+                 +--------------+                 +--------------+           +--------------+               +--------------+        +--------------+
   *   |              |    dispose()    |              |    connect()    |              |           |              |    login()    |              |        |              |
   *   |   DISPOSED   | <-------------+ | DISCONNECTED | +------------>  |  CONNECTING  | +-------> |  CONNECTED   | +---------->  | HANDSHAKING  | +----> |   ONLINE     |
   *   |              |                 |              |                 |              |           |              |               |              |        |              |
   *   +--------------+                 +--------------+                 +--------------+           +--------------+               +--------------+        +--------------+
   *
   *                                           ^                                                            +                                                     +
   *                                           |                                                            | disconnect()                                        |
   *                                           |                                                            v                                                     |
   *                                           |                                                                                                                  | disconnect()
   *                                           |                                                    +---------------+                                             |
   *                                           |                                                    |               |                                             |
   *                                           +--------------------------------------------------+ | DISCONNECTING | <-------------------------------------------+
   *                                                                                                |               |
   *                                                                                                +---------------+
   * </pre>
   */
  @NonNull
  State getState();

  /**
   * Gets the username which was used during {@link #login(String, String)}.
   * @return A {@code stringprep}ed username or an empty {@link String} if it is
   *         not yet logged in.
   */
  @NonNull
  String getUsername();

  /**
   * Gets the device ID, also known as XMPP resource.
   * @return Empty {@link String} if it is not yet logged in.
   */
  @NonNull
  String getResource();

  void setResource(@NonNull String resource);

  /**
   * Gets the {@link EventObject} stream. It never emits any errors but will
   * emit a completion when this class is disposed of.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   *   <li>
   *     {@link java.beans.PropertyChangeEvent}
   *     <ul>
   *       <li>{@code State}</li>
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
   * Gets a {@link List} of preferred {@link Locale}s. Some properties of or
   * results queried from XMPP peers are human-readable texts and therefore may
   * have multiple localized versions. For example, the "category" and "type" of
   * an {@link chat.viska.xmpp.DiscoInfo.Identity} returned by a feature query
   * in <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>. In this case, only the result matched by the {@link Locale}
   * {@link List} is returned to the method caller.
   *
   * <p>This {@link List} by default contains only the default {@link Locale}
   * set by the system.</p>
   *
   * <p>Users can directly modify the {@link List} returned by this method. But
   * how to determine the matched {@link Locale} depends on the plugin
   * developers.</p>
   */
  @NonNull
  List<Locale> getLocales();
}