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
import chat.viska.sasl.PropertiesRetriever;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Default parent of all implementations of {@link Session}.
 *
 * <p>This class does not support stream level compression.</p>
 */
public abstract class DefaultSession implements Session {

  private static final AtomicReference<DocumentBuilder> DOM_BUILDER_INSTANCE;
  private static final String[] SASL_MECHANISMS_DEFAULT = {
      "SCRAM-SHA-512",
      "SCRAM-SHA-256",
      "SCRAM-SHA-1"
  };

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
  private final List<Locale> locales = new CopyOnWriteArrayList<>(
      new Locale[] { Locale.getDefault() }
  );
  private Connection connection;
  private String username = "";
  private String resource = "";
  private AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
  private Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private HandshakerPipe handshakerPipe;
  private Compression streamCompression;

  protected DefaultSession() {
    final DefaultSession thisSession = this;
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
    xmlPipeline.addLast("handshaker", new BlankPipe());

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
   * disposing of the {@link DefaultSession} afterwards.
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
   * Sets the current {@link Session.State}. This method also triggers a
   * {@link PropertyChangeEvent} with the property name {@code State}.
   */
  protected void setState(final @NonNull State state) {
    Objects.requireNonNull(state);
    State oldState = this.state.get();
    this.state.set(state);
    triggerEvent(new PropertyChangeEvent(this, "State", oldState, state));
  }

  @Override
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
    final DefaultSession thisSession = this;
    return threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        onOpeningConnection();
        handshakerPipe = new HandshakerPipe(thisSession);
        xmlPipeline.replace("handshaker", handshakerPipe);
        xmlPipeline.getEventStream()
            .ofType(PropertyChangeEvent.class)
            .filter(new Predicate<PropertyChangeEvent>() {
              @Override
              public boolean test(PropertyChangeEvent event) throws Exception {
                return Objects.equals(event.getPropertyName(), "Resource");
              }
            })
            .subscribe(new Consumer<PropertyChangeEvent>() {
              @Override
              public void accept(PropertyChangeEvent event) throws Exception {
                resource = (String) event.getNewValue();
              }
            });
        setState(State.CONNECTED);
        return null;
      }
    });
  }

  @Override
  public Future<Void> login(final @NonNull String username,
                            final @NonNull String password) {
    if (state.get() != State.CONNECTED) {
      throw new IllegalStateException();
    }
    if (username.isEmpty() || password.isEmpty()) {
      throw new IllegalArgumentException();
    }
    setState(State.HANDSHAKING);
    this.username = username;
    return threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        xmlPipeline.start();
        //TODO
        return null;
      }
    });
  }

  @Override
  public Future<Void> login(String username, Map<String, ?> properties) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Future<Void> login(String username, PropertiesRetriever retriever) {
    throw new UnsupportedOperationException();
  }

  @Override
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

  @Override
  public void dispose() {
    throw new UnsupportedOperationException();
  }

  /**
   * Send an XML to the server. The XML needs even not be an XMPP stanza, so be
   * wary that the server will close the connection once the sent XML violates
   * any XMPP rules.
   * @throws IllegalStateException If this class is in an inappropriate {@link Session.State}.
   */
  public void send(final @NonNull Document xml) {
    if (state.get() != State.ONLINE) {
      throw new IllegalStateException();
    }
    xmlPipeline.write(xml);
  }

  @Override
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

  @Override
  public void sendStreamError() {
    if (state.get() == State.DISCONNECTED || state.get() == State.DISPOSED) {
      throw new IllegalStateException();
    }
    handshakerPipe.sendStreamError();
  }

  @Override
  @Nullable
  public Connection getConnection() {
    return connection;
  }

  @Override
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

  @Override
  @Nullable
  public Compression getStreamCompression() {
    return streamCompression;
  }

  @Override
  public void setStreamCompression(final @Nullable Compression streamCompression) {
    if (state.get() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    if (streamCompression == null) {
      this.streamCompression = null;
    } else {
      throw new IllegalArgumentException();
    }
  }

  @Override
  @NonNull
  public Observable<Document> getInboundStanzaStream() {
    return xmlPipeline.getInboundStream();
  }

  @Override
  @NonNull
  public LoggingManager getLoggingManager() {
    return loggingManager;
  }

  @Override
  @NonNull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  @NonNull
  public State getState() {
    return state.get();
  }

  @Override
  @NonNull
  public String getUsername() {
    return username;
  }

  @Override
  @NonNull
  public String getResource() {
    return resource;
  }

  @Override
  @NonNull
  public Observable<EventObject> getEventStream() {
    return eventStream;
  }

  @NonNull
  public Set<String> getFeatures() {
    return new HashSet<>(0);
  }

  @Override
  @NonNull
  public List<Locale> getLocales() {
    return locales;
  }
}