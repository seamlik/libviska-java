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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Action;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Default parent of all implementations of {@link Session}.
 *
 * <p>This class does not support stream level compression.</p>
 */
@ThreadSafe
public abstract class DefaultSession implements Session {

  /**
   * Indicates the connection is terminated.
   */
  public static class ConnectionTerminatedEvent extends EventObject {

    public ConnectionTerminatedEvent(@Nonnull final DefaultSession source) {
      super(source);
    }
  }

  /**
   * Indicates a StartTLS handshake has completed. It is usually subscribed by a
   * {@link HandshakerPipe} so that it restarts the XML stream immediately.
   */
  public static class StartTlsHandshakeCompletedEvent extends EventObject {

    public StartTlsHandshakeCompletedEvent(DefaultSession o) {
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
  private final PluginManager pluginManager;
  private final Connection connection;
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Flowable<Stanza> inboundStanzaStream;
  private final Logger logger = Logger.getLogger(this.getClass().getCanonicalName());
  private final Jid jid;
  private final Jid authzId;
  private final List<String> saslMechanisms = new ArrayList<>();

  private void log(final EventObject event) {
    logger.log(Level.FINE, event.toString());
  }

  private void log(final ExceptionCaughtEvent event) {
    logger.log(Level.WARNING, event.toString(), event.getCause());
  }

  /**
   * Default constructor.
   * @param connection Connection to the server.
   * @param streamManagement Whether to enable
   *        <a href="https://xmpp.org/extensions/xep-0198.html">Stream
   *        Management</a>
   */
  protected DefaultSession(@Nullable final Jid jid,
                           @Nullable final Jid authzId,
                           final Connection connection,
                           final boolean streamManagement) {
    Objects.requireNonNull(connection, "`connection` is absent.");
    this.connection = connection;
    this.jid = jid;
    this.authzId = authzId;

    // Event stream
    final FlowableProcessor<EventObject> unsafeEventStream = PublishProcessor.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.eventStream
        .ofType(ConnectionTerminatedEvent.class)
        .observeOn(Schedulers.io())
        .subscribe(event -> {
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
        .observeOn(Schedulers.io())
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

  @Override
  @Nonnull
  public Completable login(@Nonnull final String password) {
    Validate.notBlank(password, "`password` is absent.");
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
        null,
        this.connection.getProtocol() == Connection.Protocol.TCP
            ? Compression.AUTO
            : null
    );
  }

  @Nonnull
  @Override
  public Completable login(final CredentialRetriever retriever,
                           @Nullable final String resource,
                           final boolean registering,
                           @Nullable Compression connectionCompression,
                           @Nullable Compression tlsCompression,
                           @Nullable Compression streamCompression) {
    Objects.requireNonNull(retriever, "`retriever` is absent.");
    Objects.requireNonNull(this.jid, "JID is not provided.");

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
    handshakerPipe.getEventStream().ofType(
        HandshakerPipe.FeatureNegotiatedEvent.class
    ).filter(
        it -> it.getFeature() == StreamFeature.STARTTLS
    ).observeOn(
        Schedulers.io()
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
              .subscribe(it -> this.dispose().observeOn(Schedulers.io()).subscribe());
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
        .doOnError(it -> disconnect().observeOn(Schedulers.io()).subscribe());
  }

  @Nonnull
  @Override
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

  @Nonnull
  @Override
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

  @Override
  @Nonnull
  public StanzaReceipt send(final Document xml) {
    xmlPipeline.write(xml);
    return new StanzaReceipt(this, Maybe.empty());
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
      triggerEvent(new ExceptionCaughtEvent(this, error));
      final HandshakerPipe handshakerPipe = (HandshakerPipe)
          this.xmlPipeline.get(PIPE_HANDSHAKER);
      handshakerPipe.sendStreamError(error);
    }
  }

  @Nonnull
  @Override
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

  @Nonnull
  @Override
  public Logger getLogger() {
    return logger;
  }

  @Nullable
  @Override
  public Connection getConnection() {
    return connection;
  }

  @Nonnull
  @Override
  public Flowable<Stanza> getInboundStanzaStream() {
    return inboundStanzaStream;
  }

  @Override
  @Nonnull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  @Override
  @Nonnull
  public ReactiveObject<State> getState() {
    return state;
  }

  @Override
  @Nonnull
  public Flowable<EventObject> getEventStream() {
    return eventStream;
  }

  @Nullable
  @Override
  public Jid getJid() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getJid();
    } else {
      return null;
    }
  }

  @Nonnull
  @Override
  public Set<StreamFeature> getStreamFeatures() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getStreamFeatures();
    } else {
      return Collections.emptySet();
    }
  }
}