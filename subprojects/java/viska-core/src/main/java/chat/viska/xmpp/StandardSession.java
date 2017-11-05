package chat.viska.xmpp;

import chat.viska.commons.DomUtils;
import chat.viska.commons.ExceptionCaughtEvent;
import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.functions.Action;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.CompletableSubject;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillCloseWhenClosed;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;

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
 *   <li>Shutdown this class using {@link #disconnect()}.</li>
 * </ol>
 */
public abstract class StandardSession extends Session {

  /**
   * Indicates the network connection is terminated.
   */
  public class ConnectionTerminatedEvent extends EventObject {

    /**
     * Default constructor.
     */
    public ConnectionTerminatedEvent() {
      super(StandardSession.this);
    }
  }

  /**
   * Indicates a TLS session has finished setting up after a StartTLS request. It is usually
   * subscribed by a {@link HandshakerPipe} so that it restarts the XMPP stream immediately.
   */
  public class StartTlsDeployedEvent extends EventObject {

    /**
     * Default constructor.
     */
    public StartTlsDeployedEvent() {
      super(StandardSession.this);
    }
  }

  private static final String PIPE_HANDSHAKER = "handshaker";
  private static final String PIPE_VALIDATOR = "validator";
  private static final String PIPE_UNSUPPORTED_STANZAS_BLOCKER = "unsupported-stanzas-blocker";

  private final List<String> saslMechanisms = new ArrayList<>();
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Flowable<Stanza> inboundStanzaStream = xmlPipeline.getInboundStream().map(
      XmlWrapperStanza::new
  );
  private Connection connection;

  /**
   * Gets an instance of {@link Session} which supports all {@link Connection.Protocol}s specified.
   */
  @Nonnull
  public static StandardSession
  getInstance(@Nullable final Collection<Connection.Protocol> protocols)
      throws NoSuchProviderException {
    for (StandardSession it : ServiceLoader.load(StandardSession.class)) {
      if (protocols == null) {
        return it;
      } else if (it.getSupportedProtocols().containsAll(protocols)) {
        return it;
      }
    }
    throw new NoSuchProviderException();
  }

  public static void verifyCertificate(@Nonnull final Jid jid,
                                       @Nullable final SSLSession tlsSession)
      throws SSLPeerUnverifiedException{
    if (tlsSession == null) {
      return;
    } else {
      return; //TODO: Verify CN and domainpart of JID.
    }
  }

  protected StandardSession() {
    getEventStream().ofType(ConnectionTerminatedEvent.class).subscribe(event -> {
      changeState(State.DISCONNECTED);
      this.xmlPipeline.stopNow();
    });


    // XML Pipeline
    getState().getStream().filter(it -> it == State.CONNECTED).subscribe(it -> xmlPipeline.start());
    getState().getStream().filter(it -> it == State.DISPOSED).subscribe(
        it -> this.xmlPipeline.dispose()
    );
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundStream().filter(
        it -> getLogger().getLevel().intValue() <= Level.FINE.intValue()
    ).subscribe(
        it -> getLogger().fine("[XML sent] " + DomUtils.writeString(it)),
        ex -> triggerEvent(new ExceptionCaughtEvent(this, ex))
    );
    this.xmlPipeline.addAtInboundEnd(
        PIPE_UNSUPPORTED_STANZAS_BLOCKER,
        new UnsupportedStanzasBlockerPipe()
    );
    this.xmlPipeline.addAtInboundEnd(PIPE_HANDSHAKER, BlankPipe.getInstance());
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
  openConnection(@Nullable Compression connectionCompression,
                 @Nullable Compression tlsCompression);

  /**
   * Kills the network connection.
   */
  @Nonnull
  @CheckReturnValue
  public abstract Completable killConnection();

  /**
   * Invoked when {@link HandshakerPipe} has completed the StartTLS negotiation
   * and the client is supposed to start a TLS handshake.
   * @return Token that notifies when the TLS handshake completes.
   */
  @Nonnull
  @CheckReturnValue
  protected abstract Completable deployTls();

  /**
   * Invoked when the client and server have decided to compress the XML stream.
   * Always invoked right after a stream negotiation and before a stream
   * restart.
   */
  protected abstract void deployStreamCompression(@Nonnull Compression compression);

  /**
   * Invoked when this class is being disposed of. This method should clean up
   * system resources and return immediately.
   */
  protected abstract void onDisposing();

  /**
   * Gets the outbound stream of the XML {@link Pipeline}.
   */
  @Nonnull
  protected Flowable<Document> getXmlPipelineOutboundStream() {
    return xmlPipeline.getOutboundStream();
  }

  /**
   * Feeds an XML into the XML {@link Pipeline}.
   */
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
  protected void sendError(@Nonnull final StreamErrorException error) {
    triggerEvent(new ExceptionCaughtEvent(this, error));
    final HandshakerPipe handshakerPipe = (HandshakerPipe)
        this.xmlPipeline.get(PIPE_HANDSHAKER);
    handshakerPipe.sendStreamError(error);
  }

  @Override
  protected void sendStanza(@Nonnull Stanza stanza) {
    this.xmlPipeline.write(stanza.getXml());
  }

  /**
   * Gets all supported {@link chat.viska.xmpp.Connection.Protocol}s.
   */
  @Nonnull
  public abstract Set<Connection.Protocol> getSupportedProtocols();

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
   * Starts logging in.
   * @return Token to track the completion.
   * @throws IllegalStateException If this {@link Session} is not disconnected.
   */
  @CheckReturnValue
  @Nonnull
  public Completable login(@Nonnull final String password) {
    Validate.notBlank(password, "password");
    final CredentialRetriever retriever = (authnId, mechanism, key) -> {
      if (getLoginJid().getLocalPart().equals(authnId) && "password".equals(key)) {
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
   */
  @Nonnull
  @CheckReturnValue
  public Completable login(@Nullable final CredentialRetriever retriever,
                           @Nullable final String resource,
                           final boolean registering,
                           @Nullable Compression connectionCompression,
                           @Nullable Compression tlsCompression,
                           @Nullable Compression streamCompression) {
    if (!getLoginJid().isEmpty()) {
      Objects.requireNonNull(retriever, "retriever");
    }
    if (registering) {
      Objects.requireNonNull(getLoginJid(), "loginJid");
      Objects.requireNonNull(retriever, "retriever");
    }
    Objects.requireNonNull(this.connection, "connection");

    final Action preAction = () -> {
      getState().getAndDo(state -> {
        switch (state) {
          case DISCONNECTED:
            changeState(State.CONNECTING);
            return;
          default:
            throw new IllegalStateException();
        }
      });
    };

    final HandshakerPipe handshakerPipe = new HandshakerPipe(
        this,
        getLoginJid(),
        getAutorizationId(),
        retriever,
        this.saslMechanisms,
        resource,
        registering
    );
    handshakerPipe.getState().subscribe(it -> getLogger().fine(
        "HandshakerPipe is now " + it.name()
    ));

    final CompletableSubject handshakeResult = CompletableSubject.create();
    handshakerPipe.getState()
        .filter(it -> it == HandshakerPipe.State.COMPLETED)
        .firstElement()
        .subscribe(it -> handshakeResult.onComplete());
    handshakerPipe.getState()
        .filter(it -> it == HandshakerPipe.State.STREAM_CLOSED)
        .filter(it -> handshakerPipe.getError() != null)
        .firstElement()
        .subscribe(it -> {
          if (!(handshakeResult.hasComplete() || handshakeResult.hasThrowable())) {
            handshakeResult.onError(handshakerPipe.getError());
          }
        });
    handshakerPipe.getState()
        .filter(it -> it == HandshakerPipe.State.STREAM_CLOSED)
        .filter(it -> handshakerPipe.getError() == null)
        .firstElement()
        .subscribe(it -> {
          if (!(handshakeResult.hasComplete() || handshakeResult.hasThrowable())) {
            handshakeResult.onError(new RuntimeException());
          }
        });

    handshakerPipe.getEventStream()
        .ofType(HandshakerPipe.FeatureNegotiatedEvent.class)
        .filter(it -> it.getFeature() == StreamFeature.STARTTLS)
        .doOnComplete(() -> verifyCertificate(getLoginJid(), getTlsSession()))
        .subscribe(it -> deployTls().subscribe(
            () -> triggerEvent(new StartTlsDeployedEvent()),
            handshakeResult::onError
        ));
    this.xmlPipeline.replace("handshaker", handshakerPipe);

    final Completable result = Completable.fromAction(preAction).andThen(
        openConnection(connectionCompression, tlsCompression)
    ).doOnComplete(() -> {
      getState().getAndDo(state -> {
        changeState(State.CONNECTED);
        if (getConnection().getTlsMethod() == Connection.TlsMethod.DIRECT) {
          verifyCertificate(getLoginJid(), getTlsSession());
        }
        changeState(State.HANDSHAKING);
      });
    }).andThen(handshakeResult).doOnComplete(() -> {
      changeState(State.ONLINE);
    });
    final Action cancelling = () -> killConnection().subscribeOn(Schedulers.io()).subscribe();
    return result.doOnError(it -> cancelling.run()).doOnDispose(cancelling);
  }

  /**
   * Starts closing the XMPP stream and the network connection.
   */
  @Nonnull
  @CheckReturnValue
  public Completable disconnect() {
    switch (getState().getValue()) {
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
    final Pipe handshakerPipe = this.xmlPipeline.get("handshaker");
    final Completable action = Completable.fromAction(() -> changeState(State.DISCONNECTING));
    if (handshakerPipe instanceof HandshakerPipe) {
      return action.andThen(
          ((HandshakerPipe) handshakerPipe).closeStream()
      ).andThen(killConnection());
    } else {
      return action.andThen(killConnection());
    }
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
  public void setConnection(@Nonnull final Connection connection) {
    if (!getSupportedProtocols().contains(connection.getProtocol())) {
      throw new IllegalArgumentException();
    }
    if (getState().getValue() == State.DISCONNECTED) {
      this.connection = connection;
    } else {
      throw new IllegalStateException();
    }
  }

  @Nonnull
  @CheckReturnValue
  @WillCloseWhenClosed
  @Override
  public Completable dispose() {
    final Action action = () -> {
      changeState(State.DISPOSED);
      onDisposing();
    };
    switch (getState().getValue()) {
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

  @Nonnull
  @Override
  public Jid getNegotiatedJid() {
    final Pipe handshaker = this.xmlPipeline.get(PIPE_HANDSHAKER);
    if (handshaker instanceof HandshakerPipe) {
      return ((HandshakerPipe) handshaker).getNegotiatedJid();
    } else {
      return Jid.EMPTY;
    }
  }

  @Nonnull
  @Override
  public Flowable<Stanza> getInboundStanzaStream() {
    return inboundStanzaStream;
  }
}