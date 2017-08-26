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
import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.commons.reactive.ReactiveObject;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Action;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.subjects.MaybeSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Default parent of all implementations of {@link Session}.
 *
 * <p>This class does not support stream level compression.</p>
 *
 * <h2>Private Events</h2>
 *
 * <p>Aside from the event types declared by {@link Session}, this class also
 * emits the following events:</p>
 *
 * <ul>
 *   <li>{@link ConnectionTerminatedEvent}</li>
 * </ul>
 */
public abstract class DefaultSession implements Session {

  /**
   * Indicates the connection is terminated.
   */
  public static class ConnectionTerminatedEvent extends EventObject {

    public ConnectionTerminatedEvent(@NonNull final Object source) {
      super(source);
    }
  }

  private static final Compression DEFAULT_STREAM_COMPRESSION = null;
  private static final Set<Compression> SUPPORTED_STREAM_COMPRESSION = new HashSet<>(0);
  private static final String PIPE_NAME_HANDSHAKER = "handshaker";
  private static final String PIPE_NAME_VALIDATOR = "validator";

  private final FlowableProcessor<EventObject> eventStream;
  private final MutableReactiveObject<State> state = new MutableReactiveObject<>(
      State.DISCONNECTED
  );
  private final PluginManager pluginManager;
  private final Connection connection;
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Flowable<Stanza> inboundStanzaStream;
  private final Compression streamCompression;
  private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
  private final Jid jid;
  private final Jid authzId;
  private final List<String> saslMechanisms;

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
    this.jid = jid;
    this.authzId = authzId;
    this.saslMechanisms = saslMechanisms == null
        ? null
        : new ArrayList<>(saslMechanisms);

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
    final FlowableProcessor<EventObject> unsafeEventStream = PublishProcessor.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.eventStream.ofType(ConnectionTerminatedEvent.class).subscribe(event -> {
      synchronized (this.state) {
        this.state.setValue(State.DISCONNECTED);
      }
    });

    // XML Pipeline
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.addAtInboundEnd(PIPE_NAME_VALIDATOR, new XmlValidatorPipe());
    this.xmlPipeline.addAtInboundEnd(PIPE_NAME_HANDSHAKER, BlankPipe.getInstance());
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
    this.getState().getStream().subscribe(it -> {
      this.logger.fine("Session is now " + it.name());
    });

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
    if (!eventStream.hasComplete()) {
      eventStream.onNext(event);
    }
  }

  protected Flowable<Document> getXmlPipelineOutboundStream() {
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

    synchronized (this.state) {
      switch (this.state.getValue()) {
        case DISPOSED:
          throw new IllegalStateException("Session disposed.");
        case DISCONNECTED:
          break;
        default:
          throw new IllegalStateException("Must not login right now");
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
    this.xmlPipeline.replace("handshaker", handshakerPipe);
    final Single<HandshakerPipe.State> handshakeFinalState = handshakerPipe
        .getState()
        .getStream()
        .filter(it -> {
          final boolean isCompleted = it == HandshakerPipe.State.COMPLETED;
          final boolean isClosed = it == HandshakerPipe.State.STREAM_CLOSED;
          return isCompleted || isClosed;
        })
        .firstOrError();
    return Completable.fromSingle(onOpeningConnection().doOnComplete(() -> {
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
    final HandshakerPipe handshakerPipe = (HandshakerPipe) xmlPipeline.get("handshaker");
    return Completable.fromAction(() -> {
      synchronized (this.state) {
        this.state.setValue(State.DISCONNECTING);
      }
    }).andThen(handshakerPipe.closeStream()).andThen(onClosingConnection());
  }

  @Override
  @NonNull
  public Completable dispose() {
    final Action action = () -> {
      synchronized (this.state) {
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

  @Override
  public Maybe<Boolean> send(@NonNull final Document xml) {
    xmlPipeline.write(xml);
    return Maybe.empty();
  }

  @Override
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
    }
    final HandshakerPipe handshakerPipe = (HandshakerPipe)
        this.xmlPipeline.get(PIPE_NAME_HANDSHAKER);
    handshakerPipe.sendStreamError(error);
  }

  @Override
  public StanzaReceipt query(final String namespace,
                             final Jid target,
                             final Map<String, String> params)
      throws SAXException {
    if (this.state.getValue() == Session.State.DISPOSED) {
      throw new IllegalStateException("Session disposed.");
    }
    final String id = UUID.randomUUID().toString();
    final Document iq =  Stanza.getIqTemplate(
        Stanza.IqType.GET,
        id,
        target
    );
    final Element element = iq.createElementNS(namespace, "query");
    iq.getDocumentElement().appendChild(element);
    if (params != null) {
      Observable.fromIterable(params.entrySet()).forEach(it -> {
        element.setAttribute(it.getKey(), it.getValue());
      });
    }
    final MaybeSubject<Stanza> response = MaybeSubject.create();
    // TODO: Make this simpler
    getInboundStanzaStream()
        .filter(it -> it.getId().equals(id))
        .firstElement()
        .subscribe(it -> {
          if (it.getIqType() == Stanza.IqType.ERROR) {
            final StanzaErrorException error;
            try {
              error = StanzaErrorException.fromXml(it.getDocument());
              response.onError(error);
            } catch (StreamErrorException ex) {
              send(ex);
            }
          } else {
            response.onSuccess(it);
          }
        }, response::onError, response::onComplete);
    return new StanzaReceipt(this, send(iq), response);
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
  public Flowable<Stanza> getInboundStanzaStream() {
    return inboundStanzaStream;
  }

  @Override
  @NonNull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  @NonNull
  public ReactiveObject<State> getState() {
    return state;
  }

  @Override
  @NonNull
  public Flowable<EventObject> getEventStream() {
    return eventStream;
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
  public List<StreamFeature> getStreamFeatures() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_NAME_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getNegotiatedFeatures();
    } else {
      return Collections.emptyList();
    }
  }
}