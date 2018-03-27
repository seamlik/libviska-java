/*
 * Copyright (C) 2017-2018 Kai-Chung Yan (殷啟聰)
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

import chat.viska.commons.DomUtils;
import chat.viska.commons.ExceptionCaughtEvent;
import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.CheckReturnValue;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
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
   * Indicates what {@link Compression} does a {@link StandardSession} implementation
   * support for network connections, ranked in priority.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface ConnectionCompressionSupport {
    Compression[] value();
  }

  /**
   * Indicates what {@link Compression} does a {@link StandardSession} implementation
   * support for TLS connections, ranked in priority.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface TlsCompressionSupport {
    Compression[] value();
  }

  /**
   * Indicates what {@link Compression} does a {@link StandardSession} implementation support for
   * <a href="https://xmpp.org/extensions/xep-0138.html">Stream Compression</a>, ranked in priority.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface StreamCompressionSupport {
    Compression[] value();
  }

  /**
   * Indicates what {@link Connection.Protocol}s does a {@link StandardSession} implementation
   * support.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface ProtocolSupport {
    Connection.Protocol[] value();
  }

  /**
   * Indicates a TLS session has finished setting up after a StartTLS request. It is usually
   * subscribed by a {@link HandshakerPipe} so that it restarts the XMPP stream immediately.
   */
  public class TlsDeployedEvent extends EventObject {

    private final @Nullable Throwable error;

    /**
     * Default constructor.
     */
    public TlsDeployedEvent(@Nullable final Throwable error) {
      super(StandardSession.this);
      this.error = error;
    }

    /**
     * Gets the error when deploying TLS into the network connection.
     * @return {@code null} if TLS is successfully deployed.
     */
    @Nullable
    public Throwable getError() {
      return error;
    }
  }

  private static final String PIPE_HANDSHAKER = "handshaker";
  private static final String PIPE_UNSUPPORTED_STANZAS_BLOCKER = "unsupported-stanzas-blocker";

  private final List<String> saslMechanisms = new ArrayList<>();
  private final Pipeline<Document, Document> xmlPipeline = new Pipeline<>();
  private final Flowable<XmlWrapperStanza> inboundStanzaStream = xmlPipeline.getInboundStream().map(
      XmlWrapperStanza::new
  );
  private @MonotonicNonNull Connection connection;
  private @MonotonicNonNull HandshakerPipe handshakerPipe;

  /**
   * Gets an instance of {@link Session} which supports all {@link Connection.Protocol}s specified.
   */
  public static StandardSession newInstance(final Collection<Connection.Protocol> protocols)
      throws NoSuchProviderException {
    for (StandardSession it : ServiceLoader.load(StandardSession.class)) {
      if (protocols.isEmpty()) {
        return it;
      } else if (it.getSupportedProtocols().containsAll(protocols)) {
        return it;
      }
    }
    throw new NoSuchProviderException();
  }

  /**
   * Verifies the domain name of the certificate of a TLS session.
   */
  public static void verifyCertificate(final Jid jid, @Nullable final SSLSession tlsSession)
      throws SSLPeerUnverifiedException {
    if (tlsSession == null) {
      return;
    } else {
      return; //TODO: Verify CN and domainpart of JID.
    }
  }

  protected StandardSession() {
    stateProperty().getStream().filter(it -> it == State.DISCONNECTED).subscribe(
        it -> xmlPipeline.stopNow()
    );
    stateProperty().getStream().filter(it -> it == State.DISCONNECTING).subscribe(it -> {
      if (handshakerPipe == null) {
        killConnection();
      } else {
        handshakerPipe.closeStream();
      }
    });

    // XML Pipeline
    stateProperty().getStream().filter(it -> it == State.CONNECTED).subscribe(
        it -> xmlPipeline.start()
    );
    this.xmlPipeline.getInboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    this.xmlPipeline.getOutboundExceptionStream().subscribe(
        cause -> triggerEvent(new ExceptionCaughtEvent(this, cause))
    );
    xmlPipeline.getOutboundStream().filter(it -> {
      final Level level = getLogger().getLevel();
      return level != null && level.intValue() <= Level.FINE.intValue();
    }).subscribe(
        it -> getLogger().fine("[XML sent] " + DomUtils.writeString(it)),
        ex -> triggerEvent(new ExceptionCaughtEvent(this, ex))
    );
    this.xmlPipeline.addAtInboundEnd(
        PIPE_UNSUPPORTED_STANZAS_BLOCKER,
        new UnsupportedStanzasBlockerPipe()
    );
    xmlPipeline.addAtInboundEnd(PIPE_HANDSHAKER, BlankPipe.INSTANCE);
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
  protected abstract Completable openConnection(Compression connectionCompression,
                                                Compression tlsCompression);

  /**
   * Kills the network connection.
   */
  public abstract void killConnection();

  /**
   * Invoked when {@link HandshakerPipe} has completed the StartTLS negotiation
   * and the client is supposed to start a TLS handshake.
   * @return Token that notifies when the TLS handshake completes.
   */
  @CheckReturnValue
  protected abstract Completable deployTls();

  /**
   * Invoked when the client and server have decided to compress the XML stream.
   * Always invoked right after a stream negotiation and before a stream
   * restart.
   */
  protected abstract void deployStreamCompression(Compression compression);

  /**
   * Gets the outbound stream of the XML {@link Pipeline}.
   */
  protected final Flowable<Document> getXmlPipelineOutboundStream(
      @UnknownInitialization(StandardSession.class) StandardSession this
  ) {
    return xmlPipeline.getOutboundStream();
  }

  /**
   * Feeds an XML into the XML {@link Pipeline}.
   */
  protected void feedXmlPipeline(final Document xml) {
    final Level level = getLogger().getLevel();
    if (level != null && level.intValue() <= Level.FINE.intValue()) {
      try {
        getLogger().fine("[XML received] " + DomUtils.writeString(xml));
      } catch (TransformerException ex) {
        throw new RuntimeException(ex);
      }
    }
    xmlPipeline.read(xml);
  }

  @Override
  protected void sendError(final StreamErrorException error) {
    if (handshakerPipe == null) {
      throw new IllegalStateException();
    }
    triggerEvent(new ExceptionCaughtEvent(this, error));
    handshakerPipe.sendStreamError(error);
  }

  @Override
  protected void sendStanza(Stanza stanza) {
    this.xmlPipeline.write(stanza.toXml());
  }

  /**
   * Gets all supported {@link Connection.Protocol}s.
   */
  public Set<Connection.Protocol> getSupportedProtocols() {
    final Set<Connection.Protocol> protocols = new HashSet<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable ProtocolSupport annotation = clazz.getAnnotation(ProtocolSupport.class);
      if (annotation != null) {
        protocols.addAll(Arrays.asList(annotation.value()));
      }
      clazz = clazz.getSuperclass();
    }
    return protocols;
  }

  /**
   * Gets the connection level {@link Compression}.
   */
  public abstract Compression getConnectionCompression();

  /**
   * Gets a {@link List} of supported {@link Compression} for network connection, ranked in
   * priority.
   */
  public List<Compression> getSupportedConnectionCompression() {
    final List<Compression> compressions = new ArrayList<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable ConnectionCompressionSupport annotation = clazz.getAnnotation(
          ConnectionCompressionSupport.class
      );
      if (annotation != null) {
        Stream
            .of(annotation.value())
            .filter(it -> !compressions.contains(it))
            .forEach(compressions::add);
      }
      clazz = clazz.getSuperclass();
    }
    return compressions;
  }

  /**
   * Gets the
   * <a href="https://datatracker.ietf.org/doc/rfc3749">TLS level
   * compression</a>. It is not recommended to use because of security reason.
   * @return {@code null} if no {@link Compression} is used, always {@code null}
   *         if it is not implemented.
   */
  public abstract Compression getTlsCompression();

  /**
   * Gets a {@link List} of supported {@link Compression} for TLS connections, ranked in
   * priority.
   */
  public List<Compression> getSupportedTlsCompression() {
    final List<Compression> compressions = new ArrayList<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable TlsCompressionSupport annotation = clazz.getAnnotation(
          TlsCompressionSupport.class
      );
      if (annotation != null) {
        Stream
            .of(annotation.value())
            .filter(it -> !compressions.contains(it))
            .forEach(compressions::add);
      }
      clazz = clazz.getSuperclass();
    }
    return compressions;
  }

  /**
   * Gets the stream level {@link Compression} which is defined in
   * <a href="https://xmpp.org/extensions/xep-0138.html">XEP-0138: Stream
   * Compression</a>.
   */
  public abstract Compression getStreamCompression();

  /**
   * Gets a {@link List} of supported {@link Compression} for
   * <a href="https://xmpp.org/extensions/xep-0138.html">Stream Compression</a>, ranked in priority.
   */
  public List<Compression> getSupportedStreamCompression() {
    final List<Compression> compressions = new ArrayList<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable StreamCompressionSupport annotation = clazz.getAnnotation(
          StreamCompressionSupport.class
      );
      if (annotation != null) {
        Stream
            .of(annotation.value())
            .filter(it -> !compressions.contains(it))
            .forEach(compressions::add);
      }
      clazz = clazz.getSuperclass();
    }
    return compressions;
  }

  /**
   * Gets the
   * <a href="https://en.wikipedia.org/wiki/Transport_Layer_Security">TLS</a>
   * information of the server connection.
   * @return {@code null} if the connection is not using TLS.
   */
  @Nullable
  public abstract SSLSession getTlsSession();

  /**
   * Starts logging in. In order to perform anonymous login, use an empty password and loginJid.
   */
  public void login(final String password) {
    final List<Compression> connectionCompressions = getSupportedConnectionCompression();
    final List<Compression> streamCompressions = getSupportedStreamCompression();

    final Compression defaultConnectionCompression = connectionCompressions.isEmpty()
        ? Compression.NONE
        : connectionCompressions.get(0);
    final Compression defaultStreamCompression = streamCompressions.isEmpty()
        ? Compression.NONE
        : streamCompressions.get(0);

    final Compression connectionCompression = getConnection().getProtocol() == Connection.Protocol.TCP
        ? Compression.NONE
        : defaultConnectionCompression;
    final Compression streamCompression = getConnection().getProtocol() == Connection.Protocol.TCP
        ? defaultStreamCompression
        : Compression.NONE;

    if (!getLoginJid().isEmpty() && password.isEmpty()) {
      throw new IllegalArgumentException("Empty password.");
    }
    final CredentialRetriever retriever = (authnId, mechanism, key) -> {
      if (getLoginJid().getLocalPart().equals(authnId) && "password".equals(key)) {
        return password;
      } else {
        return null;
      }
    };
    login(
        getLoginJid().isEmpty() ? null : retriever,
        "",
        false,
        connectionCompression,
        Compression.NONE,
        streamCompression
    );
  }

  /**
   * Starts logging in.
   */
  public void login(@Nullable final CredentialRetriever retriever,
                    final String resource,
                    final boolean registering,
                    Compression connectionCompression,
                    Compression tlsCompression,
                    Compression streamCompression) {
    stateProperty().getAndDo(state -> {
      changeStateToConnecting();

      final HandshakerPipe handshakerPipe = new HandshakerPipe(
          this,
          getLoginJid(),
          getAutorizationId(),
          retriever,
          this.saslMechanisms,
          resource,
          registering
      );
      this.handshakerPipe = handshakerPipe;
      handshakerPipe.stateProperty().getStream().subscribe(it -> getLogger().fine(
          "HandshakerPipe is now " + it.name()
      ));
      handshakerPipe
          .stateProperty()
          .getStream()
          .filter(it -> it == HandshakerPipe.State.STREAM_CLOSED)
          .observeOn(Schedulers.io())
          .subscribe(it -> killConnection());
      handshakerPipe.getEventStream()
          .ofType(HandshakerPipe.FeatureNegotiatedEvent.class)
          .filter(it -> it.getFeature() == StreamFeature.STARTTLS)
          .subscribe(it -> deployTls().subscribe(
              () -> {
                triggerEvent(new TlsDeployedEvent(null));
                verifyCertificate(getLoginJid(), getTlsSession());
              },
              ex -> triggerEvent(new TlsDeployedEvent(ex))
          ));
      xmlPipeline.replace("handshaker", handshakerPipe).blockingGet();

      openConnection(connectionCompression, tlsCompression).doOnComplete(() -> {
        stateProperty().getAndDoUnsafe(SSLPeerUnverifiedException.class, (it) -> {
          changeStateToConnected();
          if (getConnection().getTlsMethod() == Connection.TlsMethod.DIRECT) {
            verifyCertificate(getLoginJid(), getTlsSession());
          }
          changeStateToHandshaking();
        });
      }).andThen(handshakerPipe.getHandshakeResult()).doOnComplete(
          // Once CONNECTED, the pipeline starts rolling, and HandshakerPipe starts working, all
          // automatically.
          this::changeStateToOnline
      ).doOnError(it -> killConnection()).subscribeOn(Schedulers.io()).subscribe();
    });
  }

  /**
   * Gets the {@link Connection} that is currently using or will be used.
   */
  public Connection getConnection() {
    if (connection == null) {
      throw new IllegalStateException();
    } else {
      return connection;
    }
  }

  /**
   * Sets the {@link Connection}.
   * @throws IllegalStateException If it is not disconnected.
   * @throws IllegalArgumentException If the {@link Connection.Protocol} is unsupported.
   */
  public void setConnection(final Connection connection) {
    stateProperty().getAndDo(state -> {
      if (state != State.DISCONNECTED) {
        throw new IllegalStateException();
      }
      if (!getSupportedProtocols().contains(connection.getProtocol())) {
        throw new IllegalArgumentException();
      }
      this.connection = connection;
    });
  }

  @Override
  public Set<StreamFeature> getStreamFeatures() {
    if (handshakerPipe == null) {
      throw new IllegalStateException();
    } else {
      return handshakerPipe.getStreamFeatures();
    }
  }

  @Override
  public Jid getNegotiatedJid() {
    if (handshakerPipe == null) {
      throw new IllegalStateException();
    } else {
      return handshakerPipe.getNegotiatedJid();
    }
  }

  @Override
  protected Flowable<XmlWrapperStanza> getInboundStanzaStream() {
    return inboundStanzaStream;
  }
}