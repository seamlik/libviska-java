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
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
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

  private static final String[] DEFAULT_SASL_MECHANISMS = {
      "SCRAM-SHA-512",
      "SCRAM-SHA-256",
      "SCRAM-SHA-1"
  };
  private static final Compression DEFAULT_STREAM_COMPRESSION = null;
  private static final Set<Compression> SUPPORTED_STREAM_COMPRESSION = new HashSet<>(0);

  private final ExecutorService threadPool = Executors.newCachedThreadPool();
  private final Subject<EventObject> eventStream;
  private final PluginManager pluginManager;
  private final Connection connection;
  private final boolean streamManagementEnabled;
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final List<String> saslMechanisms;
  private final Compression streamCompression;
  private final DocumentBuilder domBuilder;
  private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
  private AtomicReference<List<Locale>> locales = new AtomicReference<>(
      Collections.singletonList(Locale.getDefault())
  );
  private State state;

  protected static final String PIPE_NAME_HANDSHAKER = "handshaker";

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
   * Sets the current {@link Session.State}. This method also triggers a
   * {@link PropertyChangeEvent} with the property name {@code State}.
   */
  private synchronized void setState(final @NonNull State state) {
    Objects.requireNonNull(state);
    State oldState = this.state;
    this.state = state;
    triggerEvent(new PropertyChangeEvent(this, "State", oldState, state));
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
  protected DefaultSession(@NonNull final Connection connection,
                           final boolean streamManagement,
                           @Nullable final List<String> saslMechanisms,
                           @Nullable final Compression streamCompression) {
    Objects.requireNonNull(connection, "`connection` is absent.");
    this.connection = connection;
    this.streamManagementEnabled = streamManagement;

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

    // SASL
    this.saslMechanisms = saslMechanisms == null
        ? Arrays.asList(DEFAULT_SASL_MECHANISMS)
        : new ArrayList<>(saslMechanisms);

    // Event stream
    final Subject<EventObject> unsafeEventStream = PublishSubject.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.eventStream.subscribe(this::log);
    this.eventStream
        .ofType(ConnectionTerminatedEvent.class)
        .subscribe(event -> {
          setState(State.DISCONNECTED);
          xmlPipeline.stop();
        });

    // XML Pipeline
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.addAtOutboundEnd(PIPE_NAME_HANDSHAKER, new BlankPipe());

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
   * blocked I/O as much as possible as this method will be executed in a new
   * thread and the method invoking this method is non-blocked. However,
   * implementations need not perform their own synchronization as it will be
   * handled by this class.</p>
   *
   * @throws ConnectionException If the connection fails be established.
   * @throws InterruptedException If the thread running executing this method
   *                              has been interrupted.
   */
  protected abstract void onOpeningConnection()
      throws ConnectionException, InterruptedException;

  /**
   * Invoked when the user is actively closing the connection.
   *
   * <p>This method must be implemented in a single-threaded way and uses
   * blocked I/O as much as possible as this method will be executed in a new
   * thread and the method invoking this method is non-blocked. However,
   * implementations need not perform their own synchronization as it will be
   * handled by this class.</p>
   *
   * <p>This method is not invoked when the server is actively closing the
   * connection. The logic for this situation must be implemented as an event
   * handler.</p>
   */
  protected abstract void onClosingConnection() throws InterruptedException;

  /**
   * Triggers an {@link EventObject}.
   * @param event The event to be triggered.
   */
  protected void triggerEvent(@NonNull final EventObject event) {
    eventStream.onNext(event);
  }

  /**
   * Gets the XML processing pipeline. This is where {@link Document}s are
   * processed in a series of linearly ordered {@link Pipe}s, also the only
   * place where {@link Document}s are allowed to be modified.
   *
   * <p>Below are {@link Pipe}s pre-installed in this {@link Pipeline} :</p>
   *
   * <ol>
   *   <li>[Outbound end]</li>
   *   <li>{@link #PIPE_NAME_HANDSHAKER} ({@link HandshakerPipe})</li>
   *   <li>[Inbound end]</li>
   * </ol>
   *
   * <p>Implementations may add its own {@link Pipe}s but must not remove any
   * pre-installed ones.</p>
   */
  @NonNull
  protected Pipeline<Document, Document> getXmlPipeline() {
    return xmlPipeline;
  }

  @Override
  @NonNull
  public synchronized Future<?> login(@NonNull final Jid jid,
                                      @NonNull final String password) {
    Objects.requireNonNull(jid, "`jid` is absent.");
    Validate.notEmpty(password, "`password` is absent.");
    final CredentialRetriever retriever = (authnId, mechanism, key) -> {
      if (jid.getLocalPart().equals(authnId) && "password".equals(key)) {
        return password;
      } else {
        return null;
      }
    };
    return login(jid, null, retriever, null, false);
  }

  @Override
  @NonNull
  public Future<?> login(@NonNull final Jid authnId,
                         @Nullable final Jid authzId,
                         @NonNull final CredentialRetriever retriever,
                         @Nullable final String resource,
                         final boolean registering) {
    Objects.requireNonNull(authnId, "`authnId` is absent.");
    Objects.requireNonNull(retriever, "`retriever` is absent.");
    return threadPool.submit(() -> {
      if (state != State.INITIALIZED) {
        throw new IllegalStateException("Cannot login if not in INITIALIZED.");
      }
      setState(State.CONNECTING);
      final HandshakerPipe handshakerPipe = new HandshakerPipe(
          this,
          authnId,
          authzId,
          retriever,
          resource,
          registering
      );
      handshakerPipe.getEventStream().subscribe(this::log);
      this.xmlPipeline.replace("handshaker", handshakerPipe);
      try {
        onOpeningConnection();
        setState(State.CONNECTED);
        final Single<HandshakerPipe.State> handshakeTerminatedState = handshakerPipe
            .getEventStream()
            .ofType(PropertyChangeEvent.class)
            .filter(event -> {
              final boolean isCompleted = event.getNewValue() == HandshakerPipe.State.COMPLETED;
              final boolean isClosed = event.getNewValue() == HandshakerPipe.State.STREAM_CLOSED;
              return isCompleted || isClosed;
            })
            .map(event -> (HandshakerPipe.State) event.getNewValue())
            .firstOrError();
        setState(State.HANDSHAKING);
        xmlPipeline.start();
        if (handshakeTerminatedState.blockingGet() == HandshakerPipe.State.COMPLETED) {
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
      } finally {
        if (state != State.ONLINE) {
          disconnect();
        }
      }
      return null;
    });
  }

  @Override
  @NonNull
  public Future<?> reconnect() {
    throw new UnsupportedOperationException("Stream Management support required");
  }

  @Override
  @NonNull
  public Future<?> disconnect() {
    return threadPool.submit(() -> {
      switch (this.state) {
        case DISCONNECTING:
          return eventStream
              .ofType(PropertyChangeEvent.class)
              .filter(event -> event.getNewValue() == State.DISCONNECTED)
              .blockingFirst();
        case DISCONNECTED:
          return null;
        case DISPOSED:
          return null;
        default:
          break;
      }
      setState(State.DISCONNECTING);
      ((HandshakerPipe) xmlPipeline.get("handshaker")).closeStream();
      xmlPipeline.dispose();
      onClosingConnection();
      return null;
    });
  }

  @Override
  @NonNull
  public Future<?> dispose() {
    return this.threadPool.submit(() -> {
      switch (this.state) {
        case CONNECTING:
          disconnect().get();
          break;
        case CONNECTED:
          disconnect().get();
          break;
        case HANDSHAKING:
          disconnect().get();
          break;
        case ONLINE:
          disconnect().get();
          break;
        case DISCONNECTING:
          this.eventStream
              .ofType(PropertyChangeEvent.class)
              .filter(event -> event.getNewValue() == State.DISCONNECTED)
              .blockingFirst();
          break;
        case DISPOSED:
          return null;
        default:
          break;
      }
      this.xmlPipeline.dispose();
      setState(State.DISPOSED);
      this.eventStream.onComplete();
      this.threadPool.shutdownNow();
      return null;
    });
  }

  @Override
  public void close() throws Exception {
    dispose().get();
  }

  @Override
  public void send(@NonNull final Document xml) {
    if (state == State.DISPOSED) {
      throw new IllegalStateException("Session disposed.");
    }
    xmlPipeline.write(xml);
  }

  @Override
  public void send(@NonNull final String xml) throws SAXException {
    if (state == State.DISPOSED) {
      throw new IllegalStateException("Session disposed.");
    }
    final Document document;
    try {
      document = domBuilder.parse(new InputSource(new StringReader(xml)));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    document.normalizeDocument();
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
    return Collections.unmodifiableList(locales.get());
  }

  @Override
  public void setLocales(Locale... locales) {
    this.locales.set(Arrays.asList(locales));
  }

  @Override
  @NonNull
  public List<String> getSaslMechanisms() {
    return new ArrayList<>(saslMechanisms);
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