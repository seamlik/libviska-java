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

import chat.viska.commons.reactive.ReactiveObject;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.EventObject;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *     Apply some {@link Plugin}s in order to support more XMPP extensions.
 *   </li>
 *   <li>Login or register using {@code login()}.</li>
 *   <li>Shutdown the {@link Session} using {@link #disconnect()}.</li>
 * </ol>
 */
public interface Session extends AutoCloseable {

  /**
   * State of a {@link Session}.
   *
   * <h1>State diagram</h1>
   * <pre>{@code
   *             +--------------+
   *             |              |
   *             |   DISPOSED   |
   *             |              |
   *             +--------------+
   *
   *                    ^
   *                    | dispose()
   *                    +
   *
   *             +---------------+
   *             |               |                            Connection loss
   * +---------> | DISCONNECTED  |    <------------+------------------------------+-----------------------+
   * |           |               |                 |                              |                       |
   * |           +---------------+                 |                              |                       |
   * |                                             |                              |                       |
   * |                 +  ^                        |                              |                       |
   * |         login() |  | Connection loss        |                              |                       |
   * |                 v  +                        +                              +                       +
   * |
   * |           +--------------+           +--------------+               +--------------+        +--------------+
   * |           |              |           |              |               |              |        |              |
   * |           |  CONNECTING  | +-------> |  CONNECTED   | +---------->  | HANDSHAKING  | +----> |   ONLINE     |
   * |           |              |           |              |               |              |        |              |
   * |           +--------------+           +--------------+               +--------------+        +--------------+
   * |
   * |                  +                           +                             +                       +
   * |                  | disconnect()              |                             |                       |
   * |                  v                           |                             |                       |
   * |                                              |                             |                       |
   * |           +---------------+                  |                             |                       |
   * |           |               |                  |                             |                       |
   * +---------+ | DISCONNECTING | <----------------+-----------------------------+-----------------------+
   *             |               |                             disconnect()
   *             +---------------+
   * }</pre>
   */
  enum State {

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
   * @return Token to track the completion.
   * @throws IllegalStateException If this {@link Session} is not disconnected.
   */
  @NonNull
  Completable login(@NonNull String password);

  /**
   * Starts logging in.
   * @return Token to track the completion.
   * @throws IllegalStateException If this {@link Session} is not disconnected.
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
   */
  @NonNull
  Completable disconnect();

  /**
   * Starts shutting down the Session and releasing all system resources.
   */
  @NonNull
  Completable dispose();

  /**
   * Sends an XML stanza to the server. The stanza will not be validated by any
   * means, so beware that the server may close the connection once any policy
   * is violated.
   * @throws IllegalStateException If this class is disposed of.
   */
  @NonNull
  Maybe<Boolean> send(@NonNull Document xml);

  /**
   * Sends a stream error and then disconnects.
   * @throws IllegalStateException If this {@link Session} is not connected or
   *         online.
   */
  @NonNull
  void send(@NonNull StreamErrorException ex);

  @NonNull
  StanzaReceipt query(@NonNull Jid target,
                      @NonNull String namespace,
                      @Nullable Map<String, String> params)
      throws SAXException;

  /**
   * Gets the logger.
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
  Flowable<Stanza> getInboundStanzaStream();

  /**
   * Gets the plugin manager.
   */
  @NonNull
  PluginManager getPluginManager();

  /**
   * Gets the current {@link State}.
   */
  @NonNull
  ReactiveObject<State> getState();

  /**
   * Gets the negotiated {@link Jid} after handshake.
   * @return {@code null} if the handshake has not completed yet.
   */
  @Nullable
  Jid getJid();

  /**
   * Gets a stream of emitted {@link EventObject}s. It never emits any errors
   * but will emit a completion signal once this class enters
   * {@link State#DISPOSED}.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   *   <li>{@link DefaultSession.ConnectionTerminatedEvent}</li>
   * </ul>
   */
  @NonNull
  Flowable<EventObject> getEventStream();

  @NonNull
  List<StreamFeature> getStreamFeatures();
}