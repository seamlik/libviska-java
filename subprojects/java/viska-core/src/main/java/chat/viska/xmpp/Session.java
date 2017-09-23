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

import chat.viska.commons.DomUtils;
import chat.viska.commons.ExceptionCaughtEvent;
import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.commons.reactive.ReactiveObject;
import chat.viska.sasl.CredentialRetriever;
import chat.viska.xmpp.plugins.BasePlugin;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.functions.Action;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XMPP session responsible for connecting to an XMPP server, exchanging XMPP
 * stanzas and all other heavy work. This is the main entry point for an XMPP
 * client developer.
 *
 * <p>Different implementations of this type differ in the connection methods
 * they use and how they are implemented, e.g. WebSocket vs. plain TCP and
 * <a href="https://netty.io">Netty</a> vs. {@link java.nio}.</p>
 *
 * <h1>Usage</h1>
 *
 * <p>The following is a brief workflow of using a {@link Session}:</p>
 *
 * <ol>
 *   <li>Initialize a {@link Session}.</li>
 *   <li>Set a {@link Jid} for login.</li>
 *   <li>
 *     Obtain a {@link Connection} based on the domain name of an XMPP server.
 *   </li>
 *   <li>
 *     Apply some {@link Plugin}s in order to support more XMPP extensions.
 *   </li>
 *   <li>Login or register using {@code login()}.</li>
 *   <li>Shutdown the {@link Session} using {@link #disconnect()}.</li>
 * </ol>
 *
 * <p>This class does not support stream level compression.</p>
 */
@ThreadSafe
public abstract class Session implements AutoCloseable {

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
  public enum State {

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
   * Contains information of {@link Plugin}s applied on an {@link Session}.
   */
  @ThreadSafe
  public class PluginManager implements SessionAware {

    private final Set<Plugin> plugins = new HashSet<>();

    private PluginManager() {}

    /**
     * Applies a {@link Plugin}. This method does nothing if the plugin
     * has already been applied.
     * @throws IllegalArgumentException If it fails to apply the {@link Plugin}.
     */
    public synchronized void apply(final @Nonnull Class<? extends Plugin> type)
        throws IllegalArgumentException {
      Objects.requireNonNull(type);
      if (getPlugin(type) != null) {
        return;
      }
      final Plugin plugin;
      try {
        plugin = type.getConstructor().newInstance();
      } catch (Exception ex) {
        throw new IllegalArgumentException(
            "Unable to instantiate plugin " + type.getCanonicalName(),
            ex
        );
      }
      try {
        Observable.fromIterable(plugin.getDependencies()).forEach(this::apply);
      } catch (OnErrorNotImplementedException ex) {
        throw new IllegalArgumentException(
            "Unable to apply dependencies of plugin " + type.getCanonicalName(),
            ex.getCause()
        );
      }
      this.plugins.add(plugin);
      plugin.onApplied(getSession());
    }

    /**
     * Gets an applied plugin which is of a particular type.
     * @return {@code null} if the plugin cannot be found.
     */
    @Nullable
    public synchronized Plugin getPlugin(Class<? extends Plugin> type) {
      for (Plugin plugin : plugins) {
        if (type.isInstance(plugin)) {
          return plugin;
        }
      }
      return null;
    }

    @Nonnull
    public Set<Plugin> getPlugins() {
      return Collections.unmodifiableSet(plugins);
    }

    @Override
    @Nonnull
    public Session getSession() {
      return Session.this;
    }
  }

  /**
   * Indicates the connection is terminated.
   */
  public static class ConnectionTerminatedEvent extends EventObject {

    public ConnectionTerminatedEvent(@Nonnull final Session source) {
      super(source);
    }
  }

  /**
   * Indicates a StartTLS handshake has completed. It is usually subscribed by a
   * {@link HandshakerPipe} so that it restarts the XML stream immediately.
   */
  public static class StartTlsHandshakeCompletedEvent extends EventObject {

    public StartTlsHandshakeCompletedEvent(Session o) {
      super(o);
    }
  }

  private static final String PIPE_HANDSHAKER = "handshaker";
  private static final String PIPE_VALIDATOR = "validator";
  private static final String PIPE_UNSUPPORTED_STANZAS_BLOCKER = "unsupported-stanzas-blocker";

  private final FlowableProcessor<EventObject> eventStream;
  private final MutableReactiveObject<State> state = new MutableReactiveObject<>(
      State.DISCONNECTED
  );
  private final PluginManager pluginManager = new PluginManager();
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Flowable<Stanza> inboundStanzaStream;
  private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
  private final List<String> saslMechanisms = new ArrayList<>();
  private Jid jid = Jid.EMPTY;
  private Jid authzId = Jid.EMPTY;
  private Connection connection;
  private boolean neverOnline = true;

  /**
   * Gets an instance of {@link Session} which supports all {@link Connection.Protocol}s specified.
   */
  @Nonnull
  public static Session getInstance(@Nullable Collection<Connection.Protocol> protocols)
      throws NoSuchProviderException {
    for (Session it : ServiceLoader.load(Session.class)) {
      if (protocols == null) {
        return it;
      } else if (it.getSupportedProtocols().containsAll(protocols)) {
        return it;
      }
    }
    throw new NoSuchProviderException();
  }

  private void log(final EventObject event) {
    logger.log(Level.FINE, event.toString());
  }

  private void log(final ExceptionCaughtEvent event) {
    logger.log(Level.WARNING, event.toString(), event.getCause());
  }

  protected Session() {
    // Event stream
    final FlowableProcessor<EventObject> unsafeEventStream = PublishProcessor.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.eventStream.ofType(ConnectionTerminatedEvent.class).subscribe(event -> {
      synchronized (this.state) {
        this.state.setValue(State.DISCONNECTED);
      }
    });
    this.state.getStream().filter(it -> it == State.ONLINE).firstElement().subscribe(
        it -> neverOnline = false
    );

    // XML Pipeline
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline
        .getOutboundStream()
        .filter(it -> getLogger().getLevel().intValue() <= Level.FINE.intValue())
        .subscribe(it -> getLogger().fine("[XML sent] " + DomUtils.writeString(it)));
    this.xmlPipeline.addAtInboundEnd(
        PIPE_UNSUPPORTED_STANZAS_BLOCKER,
        new UnsupportedStanzasBlockerPipe()
    );
    this.xmlPipeline.addAtInboundEnd(PIPE_VALIDATOR, new XmlValidatorPipe());
    this.xmlPipeline.addAtInboundEnd(PIPE_HANDSHAKER, BlankPipe.getInstance());
    getState()
        .getStream()
        .filter(it -> it == State.CONNECTED)
        .subscribe(it -> this.xmlPipeline.start());
    getState()
        .getStream()
        .filter(it -> it == State.DISPOSED)
        .firstOrError()
        .subscribe(it -> this.xmlPipeline.dispose());
    this.eventStream
        .ofType(ConnectionTerminatedEvent.class)
        .subscribe(it -> this.xmlPipeline.stopNow());
    this.inboundStanzaStream = xmlPipeline
        .getInboundStream()
        .filter(Stanza::isStanza)
        .map(Stanza::new);
    this.xmlPipeline
        .getInboundExceptionStream()
        .ofType(XmlValidatorPipe.ValidationException.class)
        .subscribe(it -> send((StreamErrorException) it.getCause()));

    // Logging
    this.eventStream.subscribe(this::log);
    this.getState().getStream().subscribe(it -> this.logger.fine(
        "Session is now " + it.name())
    );

    getPluginManager().apply(BasePlugin.class);
  }

  /**
   * Invoked when it is about to establish a network connection to the server.
   *
   * <p>This method is supposed to perform the following tasks:</p>
   *
   * <ul>
   *   <li>Setting up a network connection to the server.</li>
   *   <li>
   *     Wiring the network data stream and the XML {@link Pipeline}
   *     <ul>
   *       <li>
   *         Fetch outbound XML data using
   *         {@link #getXmlPipelineOutboundStream()} and then send them to the
   *         server.
   *       </li>
   *       <li>
   *         Fetch inbound XML data sent by the server and feed it to
   *         {@link Session} using {@link #feedXmlPipeline(Document)}.
   *       </li>
   *     </ul>
   *   </li>
   *   <li>
   *     Setting up event handlers for connection errors or connection closing
   *     from the server.
   *   </li>
   * </ul>
   */
  @Nonnull
  @CheckReturnValue
  protected abstract Completable
  onOpeningConnection(@Nullable Compression connectionCompression,
                      @Nullable Compression tlsCompression);

  /**
   * Invoked when the user is actively closing the connection.
   */
  @Nonnull
  @CheckReturnValue
  protected abstract Completable onClosingConnection();

  /**
   * Invoked when {@link HandshakerPipe} has completed the StartTLS negotiation
   * and the client is supposed to start a TLS handshake.
   * @return Token that notifies when the TLS handshake completes.
   */
  @Nonnull
  @CheckReturnValue
  protected abstract Completable onStartTls();

  /**
   * Invoked when the client and server have decided to compress the XML stream.
   * Always invoked right after a stream negotiation and before a stream
   * restart.
   */
  protected abstract void onStreamCompression(@Nonnull Compression compression);

  /**
   * Invoked when this class is being disposed of. This method should clean
   * system resources and return immediately.
   */
  protected abstract void onDisposing();

  /**
   * Triggers an {@link EventObject}.
   * @param event The event to be triggered.
   */
  protected void triggerEvent(@Nonnull final EventObject event) {
    if (!eventStream.hasComplete()) {
      eventStream.onNext(event);
    }
  }

  protected Flowable<Document> getXmlPipelineOutboundStream() {
    return xmlPipeline.getOutboundStream();
  }

  protected void feedXmlPipeline(@Nonnull final Document xml) {
    if (getLogger().getLevel().intValue() <= Level.FINE.intValue()) {
      try {
        getLogger().fine("[XML received] " + DomUtils.writeString(xml));
      } catch (TransformerException ex) {
        throw new RuntimeException(ex);
      }
    }
    xmlPipeline.read(xml);
  }

  /**
   * Starts logging in.
   * @return Token to track the completion.
   * @throws IllegalStateException If this {@link Session} is not disconnected.
   */
  @CheckReturnValue
  @Nonnull
  public Completable login(@Nonnull final String password) {
    Validate.notBlank(password, "password");
    final CredentialRetriever retriever = (authnId, mechanism, key) -> {
      if (this.jid.getLocalPart().equals(authnId) && "password".equals(key)) {
        return password;
      } else {
        return null;
      }
    };
    return login(
        retriever,
        null,
        false,
        this.connection.getProtocol() == Connection.Protocol.TCP ? null : Compression.AUTO,
        null,
        this.connection.getProtocol() == Connection.Protocol.TCP ? Compression.AUTO : null
    );
  }

  /**
   * Starts logging in anonymously.
   * @throws IllegalStateException If this {@link Session} is not disconnected.
   */
  @Nonnull
  @CheckReturnValue
  public Completable login() {
    return login(
        null,
        null,
        false,
        this.connection.getProtocol() == Connection.Protocol.TCP ? null : Compression.AUTO,
        null,
        this.connection.getProtocol() == Connection.Protocol.TCP ? Compression.AUTO : null
    );
  }

  /**
   * Starts logging in.
   * @return Token to track the completion.
   * @throws IllegalStateException If it is disposed of or not disconnected.
   */
  @Nonnull
  @CheckReturnValue
  public Completable login(@Nullable final CredentialRetriever retriever,
                           @Nullable final String resource,
                           final boolean registering,
                           @Nullable Compression connectionCompression,
                           @Nullable Compression tlsCompression,
                           @Nullable Compression streamCompression) {
    if (!this.jid.isEmpty()) {
      Objects.requireNonNull(retriever, "retriever");
    }
    if (registering) {
      Objects.requireNonNull(this.jid, "jid");
      Objects.requireNonNull(retriever, "retriever");
    }
    Objects.requireNonNull(this.connection, "connection");

    synchronized (this.state) {
      switch (this.state.getValue()) {
        case DISPOSED:
          throw new IllegalStateException("Session disposed.");
        case DISCONNECTED:
          break;
        default:
          throw new IllegalStateException();
      }
      this.state.setValue(State.CONNECTING);
    }

    final HandshakerPipe handshakerPipe = new HandshakerPipe(
        this,
        this.jid,
        this.authzId,
        retriever,
        this.saslMechanisms,
        resource,
        registering
    );
    handshakerPipe.getState().getStream().subscribe(it -> {
      this.logger.fine("HandshakerPipe is now " + it.name());
    });
    handshakerPipe.getState().getStream()
        .filter(it -> it == HandshakerPipe.State.STREAM_CLOSED)
        .subscribe(it -> this.xmlPipeline.stopNow());
    handshakerPipe.getEventStream().ofType(
        HandshakerPipe.FeatureNegotiatedEvent.class
    ).filter(
        it -> it.getFeature() == StreamFeature.STARTTLS
    ).subscribe(it -> onStartTls().subscribe(
        () -> this.eventStream.onNext(new StartTlsHandshakeCompletedEvent(this)),
        ex ->  {
          send(new StreamErrorException(
              StreamErrorException.Condition.RESET,
              "Client-side StartTLS initialization failed."
          ));
          this.eventStream.onNext(new ExceptionCaughtEvent(this, ex));
        }
    ));
    final Single<HandshakerPipe.State> handshakeFinalState = handshakerPipe
        .getState()
        .getStream()
        .filter(it -> {
          final boolean isCompleted = it == HandshakerPipe.State.COMPLETED;
          final boolean isClosed = it == HandshakerPipe.State.STREAM_CLOSED;
          return isCompleted || isClosed;
        })
        .firstOrError();
    this.xmlPipeline.replace("handshaker", handshakerPipe);
    final Single<HandshakerPipe.State> result = onOpeningConnection(
        connectionCompression,
        tlsCompression
    ).doOnComplete(() -> {
      synchronized (this.state) {
        this.state.setValue(State.CONNECTED);
        this.state.setValue(State.HANDSHAKING);
      }
      this.xmlPipeline.start();
    }).andThen(handshakeFinalState).doOnSuccess(state -> {
      if (state == HandshakerPipe.State.COMPLETED) {
        this.state.setValue(State.ONLINE);
        if (!getStreamFeatures().contains(StreamFeature.STREAM_MANAGEMENT)) {
          getState()
              .getStream()
              .filter(it -> it == State.DISCONNECTED)
              .firstOrError()
              .subscribe(it -> this.dispose().subscribe());
        }
      } else if (handshakerPipe.getHandshakeError().getValue() != null) {
        throw handshakerPipe.getHandshakeError().getValue();
      } else if (handshakerPipe.getServerStreamError().getValue() != null) {
        throw handshakerPipe.getServerStreamError().getValue();
      } else if (handshakerPipe.getClientStreamError().getValue() != null) {
        throw handshakerPipe.getClientStreamError().getValue();
      } else {
        throw new ConnectionException(
            "Connection unexpectedly terminated."
        );
      }
    });
    return Completable
        .fromSingle(result)
        .doOnError(it -> disconnect().subscribe());
  }

  /**
   * Starts closing the XMPP stream and the network connection.
   */
  @Nonnull
  @CheckReturnValue
  public Completable disconnect() {
    synchronized (this.state) {
      switch (this.state.getValue()) {
        case DISCONNECTING:
          return getState()
              .getStream()
              .filter(it -> it == State.DISCONNECTED)
              .firstOrError()
              .toCompletable();
        case DISCONNECTED:
          return Completable.complete();
        case DISPOSED:
          return Completable.complete();
        default:
          break;
      }
    }
    final Pipe handshakerPipe = xmlPipeline.get("handshaker");
    final Completable closingStream = handshakerPipe instanceof HandshakerPipe
        ? ((HandshakerPipe) handshakerPipe).closeStream()
        : Completable.complete();
    return Completable.fromAction(() -> {
      synchronized (this.state) {
        this.state.setValue(State.DISCONNECTING);
      }
    }).andThen(closingStream).andThen(onClosingConnection());
  }

  /**
   * Starts shutting down the Session and releasing all system resources.
   */
  @Nonnull
  @CheckReturnValue
  @WillCloseWhenClosed
  public Completable dispose() {
    final Action action = () -> {
      synchronized (this.state) {
        onDisposing();
        this.state.setValue(State.DISPOSED);
        this.state.complete();
      }
      this.eventStream.onComplete();
    };
    synchronized (this.state) {
      switch (this.state.getValue()) {
        case DISPOSED:
          return Completable.complete();
        case DISCONNECTING:
          return getState()
              .getStream()
              .filter(it -> it == State.DISCONNECTED)
              .firstOrError()
              .toCompletable()
              .andThen(Completable.fromAction(action));
        case DISCONNECTED:
          return Completable.fromAction(action);
        default:
          return disconnect().andThen(Completable.fromAction(action));
      }
    }
  }

  @Override
  public void close() throws Exception {
    dispose().blockingAwait();
  }

  /**
   * Sends an XML stanza to the server. The stanza will not be validated by any
   * means, so beware that the server may close the connection once any policy
   * is violated.
   * @throws IllegalStateException If this class is disposed of.
   */
  @Nonnull
  public StanzaReceipt send(final Document xml) {
    xmlPipeline.write(xml);
    return new StanzaReceipt(this, Maybe.empty());
  }

  /**
   * Sends a stream error and then disconnects.
   * @throws IllegalStateException If this {@link Session} is not connected or
   *         online.
   */
  public void send(StreamErrorException error) {
    synchronized (this.state) {
      switch (this.state.getValue()) {
        case CONNECTED:
          break;
        case ONLINE:
          break;
        case HANDSHAKING:
          break;
        default:
          throw new IllegalStateException("Cannot send stream errors right now.");
      }
      triggerEvent(new ExceptionCaughtEvent(this, error));
      final HandshakerPipe handshakerPipe = (HandshakerPipe)
          this.xmlPipeline.get(PIPE_HANDSHAKER);
      handshakerPipe.sendStreamError(error);
    }
  }

  @Nonnull
  public IqReceipt sendIqQuery(final String namespace,
                               @Nullable final Jid target,
                               @Nullable final Map<String, String> params) {
    synchronized (this.state) {
      if (this.state.getValue() == Session.State.DISPOSED) {
        throw new IllegalStateException("Session disposed.");
      }
      final String id = UUID.randomUUID().toString();
      final Document iq =  Stanza.getIqTemplate(
          Stanza.IqType.GET,
          id,
          target
      );
      final Element element = (Element) iq.getDocumentElement().appendChild(
          iq.createElementNS(namespace, "query")
      );
      if (params != null) {
        for (Map.Entry<String, String> it : params.entrySet()) {
          element.setAttribute(it.getKey(), it.getValue());
        }
      }
      final Maybe<Stanza> response = getInboundStanzaStream()
          .filter(stanza -> id.equals(stanza.getId()))
          .firstElement()
          .doOnSuccess(stanza -> {
            if (stanza.getIqType() == Stanza.IqType.ERROR) {
              StanzaErrorException error = null;
              try {
                error = StanzaErrorException.fromXml(stanza.getXml());
              } catch (StreamErrorException ex) {
                send(ex);
              }
              if (error != null) {
                throw error;
              }
            }
          });
      return new IqReceipt(this, send(iq).getServerAcknowledment(), response);
    }

  }

  /**
   * Gets the logger.
   */
  @Nonnull
  public Logger getLogger() {
    return logger;
  }

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   */
  @Nullable
  public Connection getConnection() {
    return connection;
  }

  /**
   * Sets the {@link Connection}.
   * @throws IllegalStateException If it is not disconnected.
   * @throws IllegalArgumentException If the {@link Connection.Protocol} is unsupported.
   */
  public void setConnection(final Connection connection) {
    if (!getSupportedProtocols().contains(connection.getProtocol())) {
      throw new IllegalArgumentException();
    }
    if (getState().getValue() == State.DISCONNECTED) {
      this.connection = connection;
    } else {
      throw new IllegalStateException();
    }
  }

  /**
   * Gets a stream of inbound XMPP stanzas. The stream will not emits
   * any errors but will emit a completion after this class is disposed of.
   */
  @Nonnull
  public Flowable<Stanza> getInboundStanzaStream() {
    return inboundStanzaStream;
  }

  /**
   * Gets the plugin manager.
   */
  @Nonnull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  /**
   * Gets the current {@link State}.
   */
  @Nonnull
  public ReactiveObject<State> getState() {
    return state;
  }

  /**
   * Gets a stream of emitted {@link EventObject}s. It never emits any errors
   * but will emit a completion signal once this class enters
   * {@link State#DISPOSED}.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   *   <li>{@link ConnectionTerminatedEvent}</li>
   * </ul>
   */
  @Nonnull
  public Flowable<EventObject> getEventStream() {
    return eventStream;
  }

  @Nonnull
  public Set<StreamFeature> getStreamFeatures() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getStreamFeatures();
    } else {
      return Collections.emptySet();
    }
  }

  /**
   * Gets the connection level {@link Compression}.
   * @return {@code null} is no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  public abstract Compression getConnectionCompression();

  @Nonnull
  public abstract Set<Compression> getSupportedConnectionCompression();

  /**
   * Gets the
   * <a href="https://datatracker.ietf.org/doc/rfc3749">TLS level
   * compression</a>. It is not recommended to use because of security reason.
   * @return {@code null} if no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  @Nullable
  public abstract Compression getTlsCompression();

  @Nonnull
  public abstract Set<Compression> getSupportedTlsCompression();

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   */
  @Nullable
  public abstract Compression getStreamCompression();

  @Nonnull
  public abstract Set<Compression> getSupportedStreamCompression();

  /**
   * Gets the
   * <a href="https://en.wikipedia.org/wiki/Transport_Layer_Security">TLS</a>
   * information of the server connection.
   * @return {@code null} if the connection is not using TLS.
   */
  @Nullable
  public abstract SSLSession getTlsSession();

  /**
   * Gets all supported {@link chat.viska.xmpp.Connection.Protocol}s.
   */
  @Nonnull
  public abstract Set<Connection.Protocol> getSupportedProtocols();

  /**
   * Gets the negotiated {@link Jid} after handshake.
   * @return {@link Jid#EMPTY} if the handshake has not completed yet or it is an anonymous login.
   */
  @Nonnull
  public Jid getNegotiatedJid() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getJid();
    } else {
      return Jid.EMPTY;
    }
  }

  /**
   * Sets the {@link Jid} used for login. Use {@link Jid#EMPTY} for anonymous login.
   * @throws IllegalStateException If this {@link Session} has already been online before.
   */
  public void setLoginJid(@Nullable final Jid jid) {
    if (!neverOnline || getState().getValue() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.jid = Jid.isEmpty(jid) ? Jid.EMPTY : jid;
  }

  /**
   * Sets the authorization identity for login.
   * @throws IllegalStateException If this {@link Session} has already been online before.
   */
  public void setLoginAuthorizationId(@Nullable final Jid jid) {
    if (!neverOnline || getState().getValue() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.authzId = jid;
  }
}