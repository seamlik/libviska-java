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
import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Action;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Default parent of all implementations of {@link Session}.
 *
 * <p>This class does not support stream level compression.</p>
 */
public abstract class DefaultSession implements Session {
  private static final Compression DEFAULT_STREAM_COMPRESSION = null;
  private static final Set<Compression> SUPPORTED_STREAM_COMPRESSION = new HashSet<>(0);
  private static final String PIPE_NAME_COMPRESSION = "compression";
  private static final String PIPE_NAME_HANDSHAKER = "handshaker";

  private final Subject<EventObject> eventStream;
  private final PluginManager pluginManager;
  private final Connection connection;
  private final boolean streamManagementEnabled;
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Compression streamCompression;
  private final DocumentBuilder domBuilder;
  private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
  private final List<Locale> locales = new CopyOnWriteArrayList<>(
      new Locale[] { Locale.getDefault() }
  );
  private final Object stateLock = new Object();
  private final Jid jid;
  private final Jid authzId;
  private final List<String> saslMechanisms;
  private State state;

  private void log(@NonNull final EventObject event) {
    logger.log(Level.FINE, event.toString());
  }

  private void log(@NonNull final ExceptionCaughtEvent event) {
    logger.log(
        Level.WARNING,
        event.toString(),
        event.getCause());
  }

  /**
   * Sets the current {@link Session.State} and triggers a
   * {@link PropertyChangeEvent} with the property name {@code State}. If the
   * new {@link Session.State} is {@link Session.State#DISPOSED}, also closes
   * the event stream.
   */
  private void setState(final @NonNull State state) {
    Objects.requireNonNull(state);
    synchronized (stateLock) {
      if (state == this.state) {
        return;
      }
      State oldState = this.state;
      this.state = state;
      triggerEvent(new PropertyChangeEvent(this, "State", oldState, state));
      if (state == State.DISPOSED) {
        this.eventStream.onComplete();
      }
    }
  }

  /**
   * Default constructor.
   * @param connection Connection method ot the server.
   * @param streamManagement Whether to enable
   *        <a href="https://xmpp.org/extensions/xep-0198.html">Stream
   *        Management</a>
   * @param saslMechanisms <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>
   *        Mechanisms used during handshake. Use {@code null} to specify the
   *        default ones.
   * @param streamCompression Algorithm for stream level compression. Use
   *        {@link Compression#AUTO} to specify the default one or {@code null}
   *        to disable it.
   */
  protected DefaultSession(@Nullable final Jid jid,
                           @Nullable final Jid authzId,
                           @NonNull final Connection connection,
                           final boolean streamManagement,
                           @Nullable final List<String> saslMechanisms,
                           @Nullable final Compression streamCompression) {
    Objects.requireNonNull(connection, "`connection` is absent.");
    this.connection = connection;
    this.streamManagementEnabled = streamManagement;
    this.jid = jid;
    this.authzId = authzId;
    this.saslMechanisms = saslMechanisms == null
        ? null
        : new ArrayList<>(saslMechanisms);

    // DOM
    final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      domBuilder = builderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException("JVM does not support DOM.", ex);
    }

    // Stream Compression
    this.streamCompression = streamCompression == Compression.AUTO
        ? DEFAULT_STREAM_COMPRESSION
        : streamCompression;
    if (this.streamCompression != null
        && !SUPPORTED_STREAM_COMPRESSION.contains(this.streamCompression)) {
      throw new IllegalArgumentException(
          "Unsupported Stream Compression algorithm"
      );
    }

    // Event stream
    final Subject<EventObject> unsafeEventStream = PublishSubject.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.eventStream.subscribe(this::log);
    this.eventStream
        .ofType(ConnectionTerminatedEvent.class)
        .subscribe(event -> setState(State.DISCONNECTED));

    // XML Pipeline
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.addAtOutboundEnd(PIPE_NAME_COMPRESSION, BlankPipe.getInstance());
    this.xmlPipeline.addAtOutboundEnd(PIPE_NAME_HANDSHAKER, BlankPipe.getInstance());
    final Observable<State> stateEvents = this.eventStream
        .ofType(PropertyChangeEvent.class)
        .map(PropertyChangeEvent::getNewValue)
        .ofType(Session.State.class);
    stateEvents
        .filter(it -> it == State.CONNECTED)
        .subscribe(it -> this.xmlPipeline.start());
    stateEvents
        .filter(it -> it == State.DISCONNECTED)
        .subscribe(it -> this.xmlPipeline.stopNow());
    stateEvents
        .filter(it -> it == State.DISPOSED)
        .firstOrError()
        .subscribe(it -> this.xmlPipeline.dispose());

    this.pluginManager = new PluginManager(this);
    setState(State.INITIALIZED);
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
   *         {@link DefaultSession} using {@link #feedXmlPipeline(Document)}.
   *       </li>
   *     </ul>
   *   </li>
   *   <li>
   *     Setting up event handlers for connection errors or connection closing
   *     from the server.
   *   </li>
   * </ul>
   *
   * Upon the occurrence of an {@link Exception}, this class will invoke
   * {@link #onClosingConnection()} automatically.
   */
  @NonNull
  protected abstract Completable onOpeningConnection();

  /**
   * Invoked when the user is actively closing the connection.
   */
  protected abstract Completable onClosingConnection();

  /**
   * Triggers an {@link EventObject}.
   * @param event The event to be triggered.
   */
  protected void triggerEvent(@NonNull final EventObject event) {
    eventStream.onNext(event);
  }

  protected Observable<Document> getXmlPipelineOutboundStream() {
    return xmlPipeline.getOutboundStream();
  }

  protected void feedXmlPipeline(@NonNull final Document document) {
    xmlPipeline.read(document);
  }

  @Override
  @NonNull
  public Completable login(@NonNull final String password) {
    Validate.notEmpty(password, "`password` is absent.");
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
        Compression.AUTO,
        this.connection.getProtocol() == Connection.Protocol.TCP
            ? Compression.AUTO
            : null,
        null
    );
  }

  // Synchronized for the access of `state`.
  @Override
  @NonNull
  public Completable login(@NonNull final CredentialRetriever retriever,
                           @Nullable final String resource,
                           final boolean registering,
                           @Nullable Compression connectionCompression,
                           @Nullable Compression streamCompression,
                           @Nullable Compression tlsCompression) {
    Objects.requireNonNull(retriever, "`retriever` is absent.");

    synchronized (this.stateLock) {
      switch (this.state) {
        case DISPOSED:
          throw new IllegalStateException("Session disposed.");
        case INITIALIZED:
          break;
        case DISCONNECTED:
          break;
        default:
          throw new IllegalStateException("Must not login while " + state.toString());
      }
      setState(State.CONNECTING);
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
    handshakerPipe.getEventStream().subscribe(this::log);
    this.xmlPipeline.replace("handshaker", handshakerPipe);
    final Single<HandshakerPipe.State> handshakeFinalState = handshakerPipe
        .getEventStream()
        .ofType(PropertyChangeEvent.class)
        .filter(event -> {
          final boolean isCompleted = event.getNewValue() == HandshakerPipe.State.COMPLETED;
          final boolean isClosed = event.getNewValue() == HandshakerPipe.State.STREAM_CLOSED;
          return isCompleted || isClosed;
        })
        .map(it -> (HandshakerPipe.State) it.getNewValue())
        .firstOrError();

    return Completable.fromSingle(onOpeningConnection().doOnComplete(() -> {
      synchronized (this.stateLock) {
        setState(State.CONNECTED);
        setState(State.HANDSHAKING);
      }
      this.xmlPipeline.start();
    }).andThen(handshakeFinalState).doOnSuccess(it -> {
      if (it == HandshakerPipe.State.COMPLETED) {
        setState(State.ONLINE);
      } else if (handshakerPipe.getHandshakeError() != null) {
        throw handshakerPipe.getHandshakeError();
      } else if (handshakerPipe.getServerStreamError() != null) {
        throw handshakerPipe.getServerStreamError();
      } else if (handshakerPipe.getClientStreamError() != null) {
        throw handshakerPipe.getClientStreamError();
      } else {
        throw new ConnectionException(
            "Connection unexpectedly terminated."
        );
      }
    })).doOnError(it -> disconnect());
  }

  @Override
  @NonNull
  public Completable disconnect() {
    synchronized (this.stateLock) {
      switch (this.state) {
        case DISCONNECTING:
          return eventStream
              .ofType(PropertyChangeEvent.class)
              .filter(event -> event.getNewValue() == State.DISCONNECTED)
              .firstOrError()
              .toCompletable();
        case DISCONNECTED:
          return Completable.complete();
        case DISPOSED:
          return Completable.complete();
        default:
          setState(State.DISCONNECTING);
          break;
      }
    }
    final HandshakerPipe handshakerPipe = (HandshakerPipe) xmlPipeline.get("handshaker");
    return handshakerPipe
        .closeStream()
        .andThen(onClosingConnection());
  }

  @Override
  @NonNull
  public Completable dispose() {
    final Action action = () -> {
      setState(State.DISPOSED);
    };
    synchronized (this.stateLock) {
      switch (this.state) {
        case DISPOSED:
          return Completable.complete();
        case DISCONNECTING:
          return eventStream
              .ofType(PropertyChangeEvent.class)
              .filter(event -> event.getNewValue() == State.DISCONNECTED)
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

  @Override
  public void send(@NonNull final Document xml) {
    xmlPipeline.write(xml);
  }

  @Override
  public synchronized void send(@NonNull final String xml) throws SAXException {
    final Document document;
    synchronized (this.domBuilder) {
      try {
        document = domBuilder.parse(new InputSource(new StringReader(xml)));
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
      document.normalizeDocument();
    }
    xmlPipeline.write(document);
  }

  @Override
  public Logger getLogger() {
    return logger;
  }

  @Override
  @Nullable
  public Connection getConnection() {
    return connection;
  }

  @Override
  @Nullable
  public Compression getStreamCompression() {
    return streamCompression;
  }

  @Override
  @NonNull
  public Observable<Stanza> getInboundStanzaStream() {
    return xmlPipeline
        .getInboundStream()
        .map(document -> new Stanza(this, document));
  }

  @Override
  @NonNull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  @NonNull
  public State getState() {
    return state;
  }

  @Override
  @NonNull
  public Observable<EventObject> getEventStream() {
    return eventStream;
  }

  @NonNull
  public Set<String> getDiscoFeatures() {
    return new HashSet<>(0);
  }

  @Override
  @NonNull
  public List<Locale> getLocales() {
    return locales;
  }

  @Override
  @NonNull
  public Jid getJid() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_NAME_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getJid();
    } else {
      return null;
    }
  }

  @Override
  public boolean isStreamManagementEnabled() {
    return streamManagementEnabled;
  }
}