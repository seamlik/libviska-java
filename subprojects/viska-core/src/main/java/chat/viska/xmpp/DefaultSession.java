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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.Validate;
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

  private final ExecutorService threadPoolInstance = Executors.newCachedThreadPool();
  private final DocumentBuilder domBuilder;
  private final Subject<EventObject> eventStream = PublishSubject.create();
  private final LoggingManager loggingManager;
  private final PluginManager pluginManager;
  private final Connection connection;
  private final boolean streamManagementEnabled;
  private Locale[] locales = { Locale.getDefault() };
  private String username = "";
  private State state = State.DISCONNECTED;
  private Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private Compression streamCompression;
  private String[] saslMechanisms = {
      "SCRAM-SHA-512",
      "SCRAM-SHA-256",
      "SCRAM-SHA-1"
  };

  private synchronized void connect()
      throws ConnectionException, InterruptedException {
    switch (state) {
      case HANDSHAKING:
        return;
      case CONNECTING:
        return;
      case DISCONNECTED:
        throw new IllegalStateException("Session disconnecting.");
      case ONLINE:
        return;
      case DISPOSED:
        throw new IllegalStateException("Session disposed.");
      default:
        break;
    }
    setState(State.CONNECTING);
    onOpeningConnection();
    setState(State.CONNECTED);
  }

  protected DefaultSession(@NonNull final Connection connection,
                           final boolean streamManagement) {
    Objects.requireNonNull(connection, "`connection` is absent.");
    this.connection = connection;
    this.streamManagementEnabled = streamManagement;
    final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      domBuilder = builderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }

    final DefaultSession thisSession = this;
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
  protected synchronized void setState(final @NonNull State state) {
    Objects.requireNonNull(state);
    State oldState = this.state;
    this.state = state;
    triggerEvent(new PropertyChangeEvent(this, "State", oldState, state));
  }

  @Override
  public synchronized Future<Void> login(@NonNull final Jid jid,
                                         @NonNull final String password) {
    if (state != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    Objects.requireNonNull(jid, "`jid` is absent.");
    Validate.notEmpty(password, "`password` is absent.");
    throw new UnsupportedOperationException();

    // TODO
    /*
    setState(State.HANDSHAKING);
    this.username = jid;
    return threadPoolInstance.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        xmlPipeline.start();
        //TODO
        return null;
      }
    });
    */
  }

  @Override
  @NonNull
  public synchronized
  Future<Void> login(@NonNull final Jid authnId,
                     @Nullable final Jid authzID,
                     @NonNull final CredentialRetriever retriever,
                     @Nullable final String resource) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NonNull
  public Future<Void> register(@NonNull final Jid jid,
                               @NonNull final String password) {
    return null;
  }

  @Override
  @NonNull
  public Future<Void> register(Jid authnId,
                               Jid authzId,
                               CredentialRetriever retriever,
                               @Nullable final String resource) {
    return null;
  }

  @NonNull
  @Override
  public Future<Void> reconnect() {
    return null;
  }

  @Override
  public synchronized void disconnect() {
    // TODO: Highly unfinished!
    if (state == State.ONLINE) {
      ((HandshakerPipe) xmlPipeline.get("handshaker")).closeStream();
      xmlPipeline.shutdown();
      setState(State.DISCONNECTING);
      onClosingConnection();
      setState(State.DISCONNECTED);
    }
    if (state == State.DISCONNECTED) {
      setState(State.DISPOSED);
      if (xmlPipeline.getState() != Pipeline.State.SHUTDOWN) {
        xmlPipeline.shutdown();
      }
    }
    if (state == State.DISPOSED) {
      throw new IllegalStateException("Session already disposed.");
    }
  }

  @Override
  public synchronized void send(@NonNull final Document xml) {
    if (state != State.ONLINE) {
      throw new IllegalStateException();
    }
    xmlPipeline.write(xml);
  }

  @Override
  public synchronized void send(@NonNull final String xml) throws SAXException {
    if (state != State.ONLINE) {
      throw new IllegalStateException();
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
  public synchronized void
  setStreamCompression(@Nullable final Compression streamCompression) {
    if (state != State.DISCONNECTED) {
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
    return state;
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
  public synchronized Locale[] getLocales() {
    return Arrays.copyOf(locales, locales.length);
  }

  @Override
  public synchronized void setLocales(Locale... locales) {
    this.locales = Arrays.copyOf(locales, locales.length);
  }

  @Override
  @NonNull
  public synchronized String[] getSaslMechanisms() {
    return Arrays.copyOf(saslMechanisms, saslMechanisms.length);
  }

  @Override
  public synchronized void setSaslMechanisms(String... mechanisms) {
    this.saslMechanisms = Arrays.copyOf(mechanisms, mechanisms.length);
  }

  @Override
  public Jid getJid() {
    return null;
  }
}