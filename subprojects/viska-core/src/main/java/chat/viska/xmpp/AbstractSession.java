/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package chat.viska.xmpp;

import chat.viska.commons.ExceptionCaughtEvent;
import chat.viska.commons.pipelines.Pipe;
import chat.viska.commons.pipelines.Pipeline;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.security.cert.Certificate;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XMPP session responsible for connecting to an XMPP server, exchanging XMPP
 * stanzas and all other heavy work. This is the main entry point for an XMPP
 * client developer.
 *
 * <p>This is an abstract class, which means one needs to choose an
 * implementation or write their own. Different implementations differ in the
 * connection methods they use and how they are implemented, e.g. WebSocket vs.
 * plain TCP and <a href="https://netty.io">Netty</a> vs. {@link java.nio}.</p>
 *
 * <p>This class is thread-safe.</p>
 *
 * <h1>Usage</h1>
 *
 * <p>The following is a brief workflow of using an {@link AbstractSession}:</p>
 *
 * <ol>
 *   <li>Initialize an {@link AbstractSession} implementation.</li>
 *   <li>
 *     Obtain a {@link Connection} based on the domain name of an XMPP server.
 *   </li>
 *   <li>
 *     Adjust options like:
 *     <ul>
 *       <li>TLS</li>
 *       <li>Compression</li>
 *       <li>XMPP resource (not recommended)</li>
 *     </ul>
 *   </li>
 *   <li>
 *     Apply some {@link AbstractPlugin}s using
 *     {@link AbstractSession#getPluginManager()} in order to support more XMPP
 *     extensions like Jingle VoIP calls, IoT and OMEMO encrypted messaging.
 *     Note that {@link BasePlugin} is a built-in one and need not to be applied
 *     manually.
 *   </li>
 *   <li>
 *     Connect to the server using {@link AbstractSession#connect(Connection)}.
 *   </li>
 *   <li>Login using {@link AbstractSession#login(String, String)}.</li>
 *   <li>
 *     Possibly close the connection using {@link AbstractSession#disconnect()}
 *     and re-adjust some options.
 *   </li>
 *   <li>
 *     Shutdown the {@link AbstractSession} using
 *     {@link AbstractSession#dispose()}.
 *   </li>
 * </ol>
 */
public abstract class AbstractSession {

  /**
   * State of an {@link AbstractSession}.
   */
  public enum State {

    /**
     * Indicates a network connection to the server is established and is
     * waiting to login or perform in-band registration.
     */
    CONNECTED,

    /**
     * Indicates the {@link AbstractSession} is establishing a network
     * connection to a server.
     */
    CONNECTING,

    /**
     * Indicates the {@link AbstractSession} is currently not connected to any
     * server either because it has just been created or a precious connection
     * was closed.
     */
    DISCONNECTED,

    /**
     * Indicates the {@link AbstractSession} is closing the connection or the
     * server is doing so.
     */
    DISCONNECTING,

    /**
     * Indicates the {@link AbstractSession} has been disposed of. Any actions
     * that changes the {@link AbstractSession}'s {@link State}, e.g. starting
     * another connection, will throw an {@link IllegalStateException}.
     */
    DISPOSED,

    /**
     * Indicates the {@link AbstractSession} is handshaking with the server
     * and/or logging in.
     */
    HANDSHAKING,

    /**
     * Indicates the user is online.
     */
    ONLINE
  }

  private static final AtomicReference<DocumentBuilder> DOM_BUILDER_INSTANCE;

  static {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      DOM_BUILDER_INSTANCE = new AtomicReference<>(builderFactory.newDocumentBuilder());
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }
  }

  private final ExecutorService threadPoolInstance = Executors.newCachedThreadPool();
  private final Subject<EventObject> eventStream;
  private final LoggingManager loggingManager;
  private final PluginManager pluginManager;
  private Connection connection;
  private String username = "";
  private String resource = "";
  private AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
  private Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private HandshakerPipe handshakerPipe;
  private Compression streamCompression;

  protected AbstractSession() {
    final AbstractSession thisSession = this;
    PublishSubject<EventObject> unsafeEventStream = PublishSubject.create();
    this.eventStream = unsafeEventStream.toSerialized();

    xmlPipeline.getInboundExceptionStream().subscribe(new Consumer<Throwable>() {
      @Override
      public void accept(Throwable cause) throws Exception {
        triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
      }
    });
    xmlPipeline.getOutboundExceptionStream().subscribe(new Consumer<Throwable>() {
      @Override
      public void accept(Throwable cause) throws Exception {
        triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
      }
    });
    xmlPipeline.addLast("handshaker", new Pipe());

    this.loggingManager = new LoggingManager(this);
    this.pluginManager = new PluginManager(this);
  }

  /**
   * Invoked when it is about to establish a network connection to the server.
   *
   * <p>This method is supposed to perform the following tasks:</p>
   *
   * <ul>
   *   <li>Setting up a network connection to the server.</li>
   *   <li>
   *     Decoding inbound data into {@link Document} and encoding outbound
   *     {@link Document} into data packets.
   *   </li>
   *   <li>Wiring the network data stream and the XML {@link Pipeline}</li>
   *   <li>
   *     Setting up event handlers for connection errors or connection closing
   *     from the server.
   *   </li>
   * </ul>
   *
   * <p>This method must be implemented in a single-threaded way and uses
   * blocked I/O as much as possible for this method will be executed in a new
   * thread and the method invoking this method is non-blocked.</p>
   *
   * @throws ConnectionException If the connection fails be established.
   * @throws InterruptedException If the thread running executing this method
   *                              has been interrupted.
   */
  protected abstract void onOpeningConnection()
      throws ConnectionException, InterruptedException;

  /**
   * Invoked when the user is actively closing the connection and possibly
   * disposing of the {@link AbstractSession} afterwards.
   *
   * <p>This method must be implemented in a single-threaded way and uses
   * blocked I/O as much as possible for this method will be executed in a new
   * thread and the method invoking this method is non-blocked.</p>
   *
   * <p>This method is not invoked when the server is actively closing the
   * connection. The logic for this situation must be implemented as an event
   * handler.</p>
   */
  protected abstract void onClosingConnection();

  /**
   * Triggers an {@link EventObject}.
   * @param event The event to be triggered.
   */
  protected void triggerEvent(final @NonNull EventObject event) {
    loggingManager.log(event, null);
    eventStream.onNext(event);
  }

  /**
   * Gets the XML processing pipeline.
   *
   * <p>The XML processing pipeline is where {@link Document}s are processed in
   * a series of linearly ordered {@link Pipe}s. This is the only place where
   * {@link Document}s may be modified.</p>
   *
   * <p>All {@link Pipe}s pre-installed in this {@link Pipeline} are named:</p>
   *
   * <ol>
   *   <li>{@code handshaker}: {@link HandshakerPipe}</li>
   * </ol>
   *
   * <p>Implementations may add its own {@link Pipe}s but must not remove any
   * pre-installed ones.</p>
   */
  @NonNull
  protected Pipeline<Document, Document> getXmlPipeline() {
    return xmlPipeline;
  }

  /**
   * Sets the current {@link State}. This method also triggers a
   * {@link PropertyChangeEvent} regarding a {@code State} property.
   */
  protected void setState(final @NonNull State state) {
    Objects.requireNonNull(state);
    State oldState = this.state.get();
    this.state.set(state);
    triggerEvent(new PropertyChangeEvent(this, "State", oldState, state));
  }

  /**
   * Gets the connection level {@link Compression}.
   * @return {@code null} is no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  public abstract Compression getConnectionCompression();

  /**
   * Sets the connection level {@link Compression}.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public abstract void setConnectionCompression(@Nullable Compression method);

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
  public abstract Compression getTlsCompression();

  /**
   * Sets the TLS level {@link Compression} which is defined in
   * <a href="https://datatracker.ietf.org/doc/rfc3749">RFC 3749: Transport
   * Layer Security Protocol Compression Methods</a>.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public abstract void setTlsCompression(Compression compression);

  /**
   * Gets the standard name of the cipher suite used in the TLS connection.
   * @see SSLSession#getCipherSuite()
   * @return An empty {@link String} if TLS is disabled.
   */
  @NonNull
  public abstract String getTlsCipherSuite();

  /**
   * Gets the name of the TLS protocol used in the connection.
   * @see SSLSession#getProtocol()
   * @return An empty {@link String} if TLS is disabled.
   */
  @NonNull
  public abstract String getTlsProtocol();

  /**
   * Gets an ordered array of {@link Certificate}s sent to the server during TLS
   * handshaking.
   * @see SSLSession#getLocalCertificates()
   * @return An empty array if TLS is disabled or no certificates are sent.
   */
  @NonNull
  public abstract Certificate[] getTlsLocalCertificates();

  /**
   * Gets an ordered array of {@link Certificate}s present by the server.
   * @see SSLSession#getPeerCertificates()
   * @return An empty array if TLS is disabled.
   * @throws SSLPeerUnverifiedException If a non-certificate-based cipher suite
   *                                    such as Kerberos is used.
   */
  @NonNull
  public abstract Certificate[] getTlsPeerCertificates() throws
      SSLPeerUnverifiedException;

  /**
   * Starts connecting to a server.
   * @return A {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   * @throws ConnectionException If the connection fails to be established.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  @NonNull
  public Future<Void> connect(final @NonNull Connection connection)
      throws ConnectionException {
    Objects.requireNonNull(connection);
    switch (state.get()) {
      case HANDSHAKING:
        return ConcurrentUtils.constantFuture(null);
      case CONNECTING:
        return ConcurrentUtils.constantFuture(null);
      case DISCONNECTED:
        throw new IllegalStateException("Session is disconnecting.");
      case ONLINE:
        return ConcurrentUtils.constantFuture(null);
      case DISPOSED:
        throw new IllegalStateException("Session has been disposed of.");
      default:
        break;
    }
    setState(State.CONNECTING);
    this.connection = connection;
    return threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        onOpeningConnection();
        setState(State.CONNECTED);
        return null;
      }
    });
  }

  /**
   * Starts logging in the server.
   * @param username The local part of a {@link Jid}. It needs not to be
   *                 {@code stringprep}ed first.
   * @param password The password. It needs not to be {@code stringprep}ed first.
   * @return A {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public Future<Void> login(final @NonNull String username,
                            final @NonNull String password) {
    if (state.get() != State.CONNECTED) {
      throw new IllegalStateException();
    }
    if (username.isEmpty() || password.isEmpty()) {
      throw new IllegalArgumentException();
    }
    setState(State.HANDSHAKING);
    handshakerPipe = new HandshakerPipe(this);
    xmlPipeline.replace("handshaker", handshakerPipe);
    return threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        xmlPipeline.start();
        //TODO
        return null;
      }
    });
  }

  /**
   * Starts closing the connection. This method is non-blocking but cancellation
   * is not supported. In order to get informed when the method completes,
   * capture a {@link PropertyChangeEvent} with a property name of {@code State}
   * and a new value of {@link State#DISCONNECTED}.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void disconnect() {
    switch (state.get()) {
      case DISCONNECTED:
        return;
      case DISCONNECTING:
        return;
      case CONNECTING:
        throw new IllegalStateException("Cannot disconnect while connecting.");
      case DISPOSED:
        throw new IllegalStateException("Session disposed of.");
      default:
        break;
    }
    threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        handshakerPipe.closeStream();
        setState(State.DISCONNECTING);
        onClosingConnection();
        setState(State.DISCONNECTED);
        return null;
      }
    });
  }

  public void dispose() {
    throw new UnsupportedOperationException();
  }

  /**
   * Send an XML to the server. The XML needs even not be an XMPP stanza, so be
   * wary that the server will close the connection once the sent XML violates
   * any XMPP rules.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void send(final @NonNull Document xml) {
    if (state.get() != State.ONLINE) {
      throw new IllegalStateException();
    }
    xmlPipeline.write(xml);
  }

  /**
   * Send an XML to the server. The XML needs even not be an XMPP stanza, so be
   * wary that the server will close the connection once the sent XML violates
   * any XMPP rules.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void send(final @NonNull String xml) throws SAXException {
    if (state.get() != State.ONLINE) {
      throw new IllegalStateException();
    }
    final Document document;
    try {
      document = DOM_BUILDER_INSTANCE.get().parse(new InputSource(new StringReader(xml)));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    document.normalizeDocument();
    xmlPipeline.write(document);
  }

  /**
   * Sends a stream error if receiving any stanzas that violates XMPP rules.
   * Since stream errors are unrecoverable, the connection will be closed
   * immediately after they are sent.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void sendStreamError() {
    if (state.get() == State.DISCONNECTED || state.get() == State.DISPOSED) {
      throw new IllegalStateException();
    }
    handshakerPipe.sendStreamError();
  }

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   * @return {@code null} if it is not set yet.
   */
  @Nullable
  public Connection getConnection() {
    return connection;
  }

  /**
   * Sets the {@link Connection} to be used.
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void setConnection(final @NonNull Connection connection) {
    switch (state.get()) {
      case DISCONNECTED:
        break;
      default:
        throw new IllegalStateException();
    }
    Objects.requireNonNull(connection);
    this.connection = connection;
  }

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   * <p>WARNING: This feature is not implemented.</p>
   * @return Always {@code null} since it is not implemented yet.
   */
  @Nullable
  public Compression getStreamCompression() {
    return streamCompression;
  }

  /**
   * Sets the stream level {@link Compression} which is standardized in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   * <p>WARNING: This feature is not implemented.</p>
   * @throws IllegalStateException If this class is in an inappropriate {@link State}.
   */
  public void setStreamCompression(final @Nullable Compression streamCompression) {
    if (state.get() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.streamCompression = streamCompression;
  }

  /**
   * Gets a stream of inbound XMPP stanzas. These include {@code <iq/>},
   * {@code <message/>} and {@code <presence/>} only. The stream will not emits
   * any errors but will emit a completion after this class is disposed of.
   */
  @NonNull
  public Observable<Document> getInboundStanzaStream() {
    return xmlPipeline.getInboundStream();
  }

  /**
   * Gets a logging manager.
   */
  @NonNull
  public LoggingManager getLoggingManager() {
    return loggingManager;
  }

  /**
   * Gets a plugin manager.
   * @return
   */
  @NonNull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  /**
   * Gets the current {@link State}. When an {@link AbstractSession} is just
   * created, the {@link State} is always {@link State#DISCONNECTED}.
   * <p>The following is the state diagram:</p>
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
  public State getState() {
    return state.get();
  }

  /**
   * Gets the username, which is the local part of a {@link Jid}.
   * @return Empty {@link String} if it is not yet logged in.
   */
  @NonNull
  public String getUsername() {
    return username;
  }

  /**
   * Gets the XMPP resource.
   * @return Empty {@link String} if it is not yet logged in.
   */
  @NonNull
  public String getResource() {
    return resource;
  }

  /**
   * Gets the {@link EventObject} stream. It never emits any errors but will
   * emit a completion when this class is disposed of.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link ExceptionCaughtEvent}</li>
   *   <li>
   *     {@link PropertyChangeEvent}
   *     <ul>
   *       <li>{@code State}</li>
   *     </ul>
   *   </li>
   * </ul>
   */
  @NonNull
  public Observable<EventObject> getEventStream() {
    return eventStream;
  }

  /**
   * Gets a {@link Set} of XML namespaces representing XMPP extensions currently
   * enabled. This method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return Empty {@link Set} if no features is enabled currently.
   */
  @NonNull
  public Set<String> getFeatures() {
    return new HashSet<>(0);
  }
}