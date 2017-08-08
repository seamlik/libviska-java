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
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.EventObject;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
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
 *   <li>Initialize a {@link Session}.</li>
 *   <li>
 *     Obtain a {@link Connection} based on the domain name of an XMPP server.
 *   </li>
 *   <li>
 *     Adjust options like:
 *     <ul>
 *       <li>SASL Mechanisms.</li>
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
public interface Session extends AutoCloseable {

  /**
   * State of a {@link Session}.
   * //TODO: redraw diagram
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
   * Indicates the connection is terminated.
   */
  class ConnectionTerminatedEvent extends EventObject {

    public ConnectionTerminatedEvent(@NonNull final Object source) {
      super(source);
    }
  }

  /**
   * Starts logging in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   * @throws IllegalStateException If this class is not in
   *         {@link State#INITIALIZED}.
   */
  @NonNull
  Completable login(@NonNull String password);

  /**
   * Starts logging in.
   * @return Token to track the completion status of this method and provide a
   *         way to cancel it.
   */
  @NonNull
  Completable login(@NonNull CredentialRetriever retriever,
                    @Nullable String resource,
                    boolean registering,
                    @Nullable Compression connectionCompression,
                    @Nullable Compression streamCompression,
                    @Nullable Compression tlsCompression);

  /**
   * Starts closing the XMPP stream and the network connection.
   *
   * <p>WARNING: This method must not be interrupted, otherwise expect undefined
   * behavior.</p>
   */
  @NonNull
  Completable disconnect();

  /**
   * Starts shutting down the Session and releasing all system resources.
   *
   * <p>WARNING: This method must not be interrupted, otherwise expect undefined
   * behavior.</p>
   */
  @NonNull
  Completable dispose();

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
   * Gets the pre-configured logger.
   */
  @NonNull
  Logger getLogger();

  /**
   * Gets the connection level {@link Compression}.
   * @return {@code null} is no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getConnectionCompression();

  /**
   * Gets the
   * <a href="https://datatracker.ietf.org/doc/rfc3749">TLS level
   * compression</a>. It is not recommended to use because of security reason.
   * @return {@code null} if no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  Compression getTlsCompression();

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   */
  @Nullable
  Compression getStreamCompression();

  /**
   * Gets the
   * <a href="https://en.wikipedia.org/wiki/Transport_Layer_Security">TLS</a>
   * information of the server connection.
   * @return {@code null} if the connection is not using TLS.
   */
  @Nullable
  SSLSession getTlsSession();

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   */
  @Nullable
  Connection getConnection();

  /**
   * Gets a stream of inbound XMPP stanzas. The stream will not emits
   * any errors but will emit a completion after this class is disposed of.
   */
  @NonNull
  Observable<Stanza> getInboundStanzaStream();

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
   * Gets the negotiated {@link Jid} after handshake.
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
   *   <li>{@link ConnectionTerminatedEvent}</li>
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
   * Gets the preferred {@link Locale}s. It can be modified at anytime including
   * even when this class is in {@link State#DISPOSED}.
   *
   * <p>Some properties of or results queried from XMPP entities are
   * human-readable texts and therefore may have multiple localized versions.
   * For example, the {@code category} and {@code type} of an
   * {@link chat.viska.xmpp.DiscoInfo.Identity}. In this case, only the results
   * matched with the preferred {@link Locale}s are returned.</p>
   *
   * <p>By default it only contains the JVM's system {@link Locale} which is a
   * single one. On platforms such as Android where users can set multiple
   * {@link Locale}s, it is required to set this property manually.</p>
   *
   * @return Modifiable {@link List}.
   */
  @NonNull
  List<Locale> getLocales();

  /**
   * Indicates whether <a href="https://xmpp.org/extensions/xep-0198.html">Stream
   * Management</a> is enabled.
   */
  boolean isStreamManagementEnabled();
}