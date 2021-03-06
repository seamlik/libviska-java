/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
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
import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.sasl.AuthenticationException;
import chat.viska.sasl.Client;
import chat.viska.sasl.ClientFactory;
import chat.viska.sasl.CredentialRetriever;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.CompletableSubject;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import rxbeans.ExceptionCaughtEvent;
import rxbeans.MutableProperty;
import rxbeans.Property;
import rxbeans.StandardProperty;

/**
 * For handling handshaking, login and management of an XMPP stream.
 *
 * <h2>Usage</h2>
 *
 * <p>The handshake starts once the {@link Pipeline} starts running or it is
 * already running when this Pipe is added to a Pipeline. In order to get
 * get notified once the handshake/login completes, subscribe to a
 * {@link PropertyChangeEvent} in which {@code State} has changed to
 * {@link State#COMPLETED}.</p>
 *
 * <h2>Notes on Behavior</h2>
 *
 * <p>Some behavior of its handshaking process differs from XMPP standards,
 * either because of security considerations or development convenience. These
 * notes may hopefully help contributors understand the logic more easily.</p>
 *
 * <h3>XML Framing</h3>
 *
 * <p>XMPP is not a protocol of streaming multiple XML documents but a single
 * large XML document, individual top-level elements are not necessarily legally
 * independent XML documents. Because {@literal libviska-java} uses
 * {@link Document}s to represent each top-level elements in the XMPP stream,
 * it assumes the XML framing conforms to
 * <a href="https://datatracker.ietf.org/doc/rfc7395">RFC 7395</a>.
 * Implementations of {@link Session} should take care of the
 * conversion.</p>
 *
 * <h3>SASL</h3>
 *
 * <p>According to <a href="https://datatracker.ietf.org/doc/rfc6120">RFC
 * 6120</a>, the client may retry the
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> authentication
 * for a number of times or even try another mechanism if the authentication
 * fails. However, this class aborts the handshake immediately after the
 * authentication fails.</p>
 */
public class HandshakerPipe extends BlankPipe implements rxbeans.Object {

  /**
   * Indicates the state of a {@link HandshakerPipe}.
   */
  public enum State {

    /**
     * Indicates the pipe is newly created.
     */
    INITIALIZED,

    /**
     * Indicates a stream opening has been sent and awaiting a stream opening
     * from the server. During this state, any data received that is not a
     * {@link Document} will be forwarded as is.
     */
    STARTED,

    /**
     * Indicates an negotiation of stream features is happening. During this
     * state, any data received that is not a {@link Document} will be forwarded
     * as is.
     */
    NEGOTIATING,

    /**
     * Indicates the handshake is completed. During this state, any data
     * received that is not a {@link Document} will be forwarded as is.
     */
    COMPLETED,

    /**
     * Indicates a stream closing has been issued and awaiting for a closing
     * confirmation from the server. During this state, any data received that
     * is not a {@link Document} will be forwarded as is. The {@link Session}
     * is still functional as usual.
     *
     * <p>If it is the server who sends a stream closing first, a responsive
     * stream closing will be sent immediately and this class will directly
     * enter {@link #STREAM_CLOSED}.</p>
     */
    STREAM_CLOSING,

    /**
     * Indicates there is no XMPP stream running at the moment. This is the
     * initial state when a {@link HandshakerPipe} is newly created. During this
     * state, any data received that is a {@link Document} will be forwarded as
     * is but {@link Document}s will be discarded.
     */
    STREAM_CLOSED,

    /**
     * Indicates the handshaker has been removed from a {@link Pipeline} or the
     * {@link Pipeline} has been disposed. During this state, an
     * {@link IllegalStateException} will be thrown upon receiving any data.
     * This is a terminal state, which means the event stream will terminate and
     * the Pipe will no longer be able to enter other state.
     */
    DISPOSED
  }

  /**
   * Indicates a {@link StreamFeature} has just been negotiated.
   */
  public static class FeatureNegotiatedEvent extends EventObject {

    private final StreamFeature feature;

    public FeatureNegotiatedEvent(final HandshakerPipe source,
                                  final StreamFeature feature) {
      super(source);
      this.feature = feature;
    }

    /**
     * Gets the {@link StreamFeature} that was negotiated.
     */
    public StreamFeature getFeature() {
      return feature;
    }
  }

  private static final Version SUPPORTED_VERSION = new Version(1, 0);
  private static final List<StreamFeature> FEATURES_ORDER = Arrays.asList(
      StreamFeature.STARTTLS,
      StreamFeature.SASL,
      StreamFeature.RESOURCE_BINDING
  );
  private static final Set<StreamFeature> INFORMATIONAL_FEATURES = new HashSet<>(
      Observable
          .fromArray(StreamFeature.class.getEnumConstants())
          .filter(StreamFeature::isInformational)
          .toList()
          .blockingGet()
  );

  @GuardedBy("itself")
  private final MutableProperty<State> state = new StandardProperty<>(State.INITIALIZED);
  private final StandardSession session;
  private final Set<StreamFeature> negotiatedFeatures = new HashSet<>();
  private final Jid loginJid;
  private final Jid authzId;
  private final @Nullable CredentialRetriever retriever;
  private final Base64.Encoder base64Encoder = Base64.getEncoder();
  private final Base64.Decoder base64Decoder = Base64.getDecoder();
  private final String presetResource;
  private final List<String> saslMechanisms = new ArrayList<>();
  private final FlowableProcessor<EventObject> eventStream = PublishProcessor
      .<EventObject>create()
      .toSerialized();
  private final CompositeDisposable bin = new CompositeDisposable();
  private final CompletableSubject handshakeResult = CompletableSubject.create();
  private @MonotonicNonNull Pipeline<?, ?> pipeline;
  private @Nullable Client saslClient;
  private @Nullable StreamFeature negotiatingFeature;
  private Jid negotiatedJid = Jid.EMPTY;
  private String resourceBindingIqId = "";

  private boolean checkIfAllMandatoryFeaturesNegotiated() {
    final Set<StreamFeature> notNegotiated = new HashSet<>(FEATURES_ORDER);
    notNegotiated.removeAll(negotiatedFeatures);
    return !Observable
        .fromIterable(notNegotiated)
        .any(StreamFeature::isMandatory)
        .blockingGet();
  }

  private void sendStreamOpening() {
    try {
      this.pipeline.write(DomUtils.readDocument(String.format(
          "<open xmlns=\"%1s\" to=\"%2s\" version=\"1.0\"/>",
          CommonXmlns.STREAM_OPENING_WEBSOCKET,
          loginJid.getDomainPart()
      )));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void sendStreamClosing() {
    try {
      this.pipeline.write(DomUtils.readDocument(String.format(
          "<close xmlns=\"%1s\"/>",
          CommonXmlns.STREAM_OPENING_WEBSOCKET
      )));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void consumeStreamOpening(final Document document) {
    Version serverVersion = null;
    final String serverVersionText = document.getDocumentElement().getAttribute(
        "version"
    );
    try {
      serverVersion = new Version(serverVersionText);
    } catch (IllegalArgumentException ex) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_VERSION,
          serverVersionText
      ));
    }
    if (!SUPPORTED_VERSION.equals(serverVersion)) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_VERSION,
          serverVersionText
      ));
    }
    final String serverDomain = document
        .getDocumentElement()
        .getAttribute("from");
    if (!serverDomain.equals(this.loginJid.getDomainPart())) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.INVALID_FROM,
          serverDomain
      ));
    }
  }

  /**
   * Checks the feature list and see if any feature should negotiate next. Also
   * flags any informational {@link StreamFeature}s as negotiated. Also sets the
   * field {@code negotiatingFeature}.
   * @param document XML sent by the server.
   * @return {@link StreamFeature} selected to negotiate.
   */
  @Nullable
  private Element consumeStreamFeatures(final Document document) {
    final List<Node> announcedFeatures = DomUtils.convertToList(
        document.getDocumentElement().getChildNodes()
    );
    if (announcedFeatures.size() == 0) {
      return null;
    }

    for (StreamFeature informational : INFORMATIONAL_FEATURES) {
      for (Node announced : announcedFeatures) {
        if (informational.getNamespace().equals(announced.getNamespaceURI())
            && informational.getName().equals(announced.getLocalName())) {
          if (this.negotiatedFeatures.add(informational)) {
            this.eventStream.onNext(
                new FeatureNegotiatedEvent(this, informational)
            );
          }
        }
      }
    }

    for (StreamFeature supported : FEATURES_ORDER) {
      for (Node announced : announcedFeatures) {
        if (supported.getNamespace().equals(announced.getNamespaceURI())
            && supported.getName().equals(announced.getLocalName())) {
          this.negotiatingFeature = supported;
          return (Element) announced;
        }
      }
    }

    return null;
  }

  private void initiateStartTls() {
    try {
      this.pipeline.write(DomUtils.readDocument(String.format(
          "<starttls xmlns=\"%1s\"/>",
          CommonXmlns.STARTTLS
      )));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void initiateSasl(final Element mechanismsElement) throws SAXException {
    final List<String> mechanisms = Observable.fromIterable(DomUtils.convertToList(
        mechanismsElement.getElementsByTagName("mechanism")
    )).map(Node::getTextContent)
        .toList()
        .blockingGet();
    this.saslClient = new ClientFactory(this.saslMechanisms).newClient(
        mechanisms,
        this.loginJid.getLocalPart(),
        this.authzId.toString(),
        this.retriever
    );
    if (this.saslClient == null) {
      this.pipeline.write(DomUtils.readDocument(String.format(
          "<abort xmlns=\"%1s\"/>",
          CommonXmlns.SASL
      )));
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.POLICY_VIOLATION,
          "No supported SASL mechanisms."
      ));
    }
    String msg = "";
    if (this.saslClient.isClientFirst()) {
      try {
        msg = base64Encoder.encodeToString(this.saslClient.respond());
      } catch (AuthenticationException ex) {
        throw new RuntimeException(ex);
      }
      if (msg.isEmpty()) {
        msg = "=";
      }
    }
    this.pipeline.write(DomUtils.readDocument(String.format(
        "<auth xmlns=\"%1s\" mechanism=\"%2s\">%3s</auth>",
        CommonXmlns.SASL,
        this.saslClient.getMechanism(),
        msg
    )));
  }

  private void initiateResourceBinding() {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    this.resourceBindingIqId = UUID.randomUUID().toString();
    final Document iq = XmlWrapperStanza.createIq(
        Stanza.IqType.SET,
        resourceBindingIqId,
        Jid.EMPTY,
        Jid.EMPTY
    );
    final Element bind = (Element) iq.getDocumentElement().appendChild(iq.createElementNS(
        CommonXmlns.RESOURCE_BINDING,
        "bind"
    ));
    if (!this.presetResource.isEmpty()) {
      final Element resource = (Element) bind.appendChild(
          iq.createElement("resource")
      );
      resource.setTextContent(this.presetResource);
    }
    this.pipeline.write(iq);
  }

  private void consumeStartTls(final Document xml) {
    String s = xml.getDocumentElement().getLocalName();
    if ("proceed".equals(s)) {
      this.negotiatedFeatures.add(StreamFeature.STARTTLS);
      this.eventStream.onNext(
          new FeatureNegotiatedEvent(this, StreamFeature.STARTTLS)
      );
      this.negotiatingFeature = null;
    } else if ("failure".equals(s)) {
      handshakeResult.onError(new Exception("Server failed to proceed StartTLS."));
    } else {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
      ));
    }
  }

  private void consumeSasl(final Document document) throws SAXException {
    if (saslClient == null) {
      throw new IllegalStateException();
    }
    final String msg = document.getDocumentElement().getTextContent();

    if (this.saslClient.isCompleted() && StringUtils.isNotBlank(msg)) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.POLICY_VIOLATION,
          "Not receiving SASL messages at the time."
      ));
    }

    switch (document.getDocumentElement().getTagName()) {
      case "failure":
        this.negotiatedFeatures.remove(this.negotiatingFeature);
        this.negotiatingFeature = null;
        closeStream();
        handshakeResult.onError(new AuthenticationException(
            AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED
        ));
        break;
      case "success":
        if (StringUtils.isNotBlank(msg)) {
          this.saslClient.acceptChallenge(base64Decoder.decode(msg));
        }
        if (!this.saslClient.isCompleted()) {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "SASL not finished yet."
          ));
        } else if (this.saslClient.getError() != null) {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.NOT_AUTHORIZED,
              "Incorrect server proof."
          ));
        } else {
          this.negotiatedFeatures.add(this.negotiatingFeature);
          this.eventStream.onNext(
              new FeatureNegotiatedEvent(this, this.negotiatingFeature)
          );
          this.negotiatingFeature = null;
          sendStreamOpening();
        }
        break;
      case "challenge":
        this.saslClient.acceptChallenge(base64Decoder.decode(msg));
        if (!this.saslClient.isCompleted()) {
          try {
            final byte[] response = this.saslClient.respond();
            this.pipeline.write(DomUtils.readDocument(String.format(
                "<response xmlns=\"%1s\">%2s</response>",
                CommonXmlns.SASL,
                base64Encoder.encodeToString(response)
            )));
          } catch (AuthenticationException ex) {
            this.pipeline.write(DomUtils.readDocument(String.format(
                "<abort xmlns=\"%1s\"/>",
                CommonXmlns.SASL
            )));
            sendStreamError(new StreamErrorException(
                StreamErrorException.Condition.POLICY_VIOLATION,
                "Malformed SASL message."
            ));
          }
        }
        break;
      default:
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
        ));
    }
  }

  private void consumeStreamCompression(Document document) {
    throw new UnsupportedOperationException();
  }

  private void consumeResourceBinding(final Document document) {
    if (!resourceBindingIqId.equals(document.getDocumentElement().getAttribute("id"))) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.NOT_AUTHORIZED
      ));
    } else if ("error".equals(document.getDocumentElement().getAttribute("type"))) {
      try {
        handshakeResult.onError(StanzaErrorException.fromXml(document));
      } catch (StreamErrorException ex) {
        sendStreamError(ex);
      }
    } else if ("result".equals(document.getDocumentElement().getAttribute("type"))) {
      final Optional<Element> bindElement = DomUtils
          .convertToList(document.getDocumentElement().getChildNodes())
          .stream()
          .filter(it -> "bind".equals(it.getLocalName()))
          .filter(it -> CommonXmlns.RESOURCE_BINDING.equals(it.getNamespaceURI()))
          .map(it -> (Element) it)
          .findFirst();
      if (!bindElement.isPresent()) {
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.INVALID_XML,
            "Empty result."
        ));
        return;
      }
      final String[] results = DomUtils
          .convertToList(bindElement.get().getChildNodes())
          .stream()
          .map(it -> (Element) it)
          .filter(it -> "jid".equals(it.getLocalName()))
          .filter(it -> CommonXmlns.RESOURCE_BINDING.equals(it.getNamespaceURI()))
          .map(it -> StringUtils.defaultIfBlank(it.getTextContent(), "").split(" "))
          .findFirst()
          .orElse(new String[0]);

      switch (results.length) {
        case 1:
          this.negotiatedJid = new Jid(results[0]);
          break;
        case 2:
          if (new Jid(results[0]).equals(this.loginJid)) {
            this.negotiatedJid = new Jid(
                this.loginJid.getLocalPart(),
                this.loginJid.getDomainPart(),
                results[1]
            );
          } else {
            sendStreamError(new StreamErrorException(
                StreamErrorException.Condition.INVALID_XML,
                "Resource Binding result contains incorrect JID."
            ));
          }
          break;
        default:
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.INVALID_XML,
              "Malformed JID syntax."
          ));
          break;
      }
    }
    if (!this.negotiatedJid.isEmpty()) {
      if (this.negotiatedFeatures.add(this.negotiatingFeature)) {
        this.eventStream.onNext(
            new FeatureNegotiatedEvent(this, StreamFeature.RESOURCE_BINDING)
        );
      }
      this.negotiatingFeature = null;
    }
  }

  private void start() {
    synchronized (state) {
      if (state.get() != State.INITIALIZED) {
        throw new IllegalStateException();
      }
      state.change(State.STARTED);
    }
    sendStreamOpening();
  }


  /**
   * Default constructor.
   * @param loginJid Required when registering.
   * @param authzId Authorization ID, optional.
   * @param saslMechanisms <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>
   *        Mechanisms used during handshake. Use an empty {@link Set} in order to use the default.
   * @param resource XMPP Resource. If empty, the server will generate a random one.
   * @param registering Indicates if the handshake includes in-band
   *                    registration.
   */
  public HandshakerPipe(final StandardSession session,
                        final Jid loginJid,
                        final Jid authzId,
                        @Nullable final CredentialRetriever retriever,
                        final List<String> saslMechanisms,
                        final String resource,
                        final boolean registering) {
    if (registering) {
      Validate.isTrue(
          !Jid.isEmpty(loginJid),
          "\"loginJid\" must be provided when registering."
      );
    }
    if (!Jid.isEmpty(loginJid)) {
      Objects.requireNonNull(
          retriever,
          "A credential retriever must be provided when registering."
      );
    }
    this.session = session;
    this.loginJid = loginJid;
    this.authzId = authzId;
    this.retriever = retriever;
    if (saslMechanisms.isEmpty()) {
      this.saslMechanisms.add("SCRAM-SHA-1");
    } else {
      this.saslMechanisms.addAll(saslMechanisms);
    }
    presetResource = resource;

    state.getStream().filter(
        it -> it == State.STREAM_CLOSED || it == State.COMPLETED || it == State.DISPOSED
    ).filter(
        it -> !handshakeResult.hasThrowable() && !handshakeResult.hasComplete()
    ).firstOrError().subscribe(state -> {
      if (state == State.COMPLETED) {
        handshakeResult.onComplete();
      } else {
        handshakeResult.onError(new CancellationException());
      }
    });
  }

  /**
   * Closes the XMPP stream.
   */
  public void closeStream() {
    state.getAndDo(state -> {
      switch (state) {
        case INITIALIZED:
          this.state.change(State.STREAM_CLOSED);
          return;
        case STREAM_CLOSING:
          break;
        case STREAM_CLOSED:
          return;
        case DISPOSED:
          return;
        default:
          //TODO: Timeout
          sendStreamClosing();
          this.state.change(State.STREAM_CLOSING);
          break;
      }
    });
  }

  /**
   * Sends a stream error and closes the connection.
   */
  public void sendStreamError(final StreamErrorException error) {
    if (pipeline == null) {
      throw new IllegalStateException();
    }
    session.stateProperty().getAndDo(state -> {
      if (state == Session.State.ONLINE || state == Session.State.HANDSHAKING) {
        pipeline.write(error.toXml());
      }
      closeStream();
      eventStream.onNext(new ExceptionCaughtEvent(this, error));
      if (!handshakeResult.hasComplete() && !handshakeResult.hasThrowable()) {
        handshakeResult.onError(error);
      }
    });
  }

  /**
   * Gets the JID negotiated during Resource Binding.
   * @return {@code null} if the negotiation is not completed yet.
   */
  public Jid getNegotiatedJid() {
    return negotiatedJid;
  }

  /**
   * Gets the current {@link State} of this class.
   */
  public Property<State> stateProperty() {
    return state;
  }

  /**
   * Gets the handshake result. Signals {@link CancellationException} if the network connection is
   * lost during the handshake. May signals other {@link Exception}s depending on the situation.
   */
  public Completable getHandshakeResult() {
    return handshakeResult;
  }

  public Set<StreamFeature> getStreamFeatures() {
    return Collections.unmodifiableSet(negotiatedFeatures);
  }

  @Override
  public Flowable<EventObject> getEventStream() {
    return eventStream;
  }

  /**
   * Invoked when the Pipe is reading data.
   * @throws AuthenticationException If a failure occurred during an SASL
   *         negotiation.
   */
  @Override
  public void onReading(final Pipeline<?, ?> pipeline,
                        final Object toRead,
                        final List<Object> toForward) throws Exception {
    state.getAndDoUnsafe(Exception.class, state -> {
      if (state == State.DISPOSED) {
        throw new IllegalStateException();
      }

      final Document document;
      if (toRead instanceof Document) {
        document = (Document) toRead;
      } else {
        document = null;
      }
      if (document == null) {
        super.onReading(pipeline, toRead, toForward);
        return;
      }

      if (state == State.STREAM_CLOSED || state == State.INITIALIZED) {
        return;
      }

      final String rootName = document.getDocumentElement().getLocalName();
      final String rootNs = document.getDocumentElement().getNamespaceURI();

      if ("open".equals(rootName) && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
        switch (state) {
          case STARTED:
            consumeStreamOpening(document);
            this.state.change(State.NEGOTIATING);
            break;
          case NEGOTIATING:
            consumeStreamOpening(document);
            break;
          case COMPLETED:
            sendStreamError(new StreamErrorException(
                StreamErrorException.Condition.CONFLICT,
                "Server unexpectedly restarted the stream."
            ));
            break;
          default:
            break;
        }
      } else if ("close".equals(rootName) && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
        switch (state) {
          case STREAM_CLOSING:
            break;
          default:
            sendStreamClosing();
            break;
        }
        this.state.change(State.STREAM_CLOSED);
      } else if ("features".equals(rootName) && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
        if (state == State.NEGOTIATING) {
          final Element selectedFeature = consumeStreamFeatures(document);
          if (selectedFeature == null) {
            if (checkIfAllMandatoryFeaturesNegotiated()) {
              this.state.change(State.COMPLETED);
            } else {
              sendStreamError(new StreamErrorException(
                  StreamErrorException.Condition.UNSUPPORTED_FEATURE,
                  "We have mandatory features that you do not support."
              ));
            }
          } else {
            if (negotiatingFeature == StreamFeature.STARTTLS) {
              this.session.getLogger().fine("Negotiating StartTLS.");
              initiateStartTls();
            } else if (negotiatingFeature == StreamFeature.SASL) {
              this.session.getLogger().fine("Negotiating SASL.");
              initiateSasl(selectedFeature);
            } else if (negotiatingFeature == StreamFeature.RESOURCE_BINDING) {
              this.session.getLogger().fine("Negotiating Resource Binding.");
              initiateResourceBinding();
            } else {
              sendStreamError(new StreamErrorException(
                  StreamErrorException.Condition.UNSUPPORTED_FEATURE
              ));
            }
          }
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Re-negotiating features not allowed."
          ));
        }
      } else if (CommonXmlns.STARTTLS.equals(rootNs)) {
        if (state == State.NEGOTIATING && this.negotiatingFeature == StreamFeature.STARTTLS) {
          consumeStartTls(document);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Not negotiating StartTLS at the time."
          ));
        }
      } else if (CommonXmlns.SASL.equals(rootNs)) {
        if (state == State.NEGOTIATING && negotiatingFeature == StreamFeature.SASL) {
          consumeSasl(document);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Not negotiating SASL at the time."
          ));
        }
      } else if ("iq".equals(rootName)) {
        if (state == State.NEGOTIATING && this.negotiatingFeature == StreamFeature.RESOURCE_BINDING) {
          consumeResourceBinding(document);
        } else if (state == State.COMPLETED) {
          super.onReading(pipeline, toRead, toForward);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.NOT_AUTHORIZED,
              "Stanzas not allowed before stream negotiation completes."
          ));
        }
      } else if ("error".equals(rootName) && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
        eventStream.onNext(new ExceptionCaughtEvent(this, StreamErrorException.fromXml(document)));
        closeStream();
      } else {
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
        ));
      }
    });
    if (handshakeResult.hasThrowable()) {
      closeStream();
    }
  }

  @Override
  public void onAddedToPipeline(final Pipeline<?, ?> pipeline) {
    // TODO: Support for stream resumption
    if (state.get() != State.INITIALIZED) {
      throw new IllegalStateException();
    }

    /* Resource Binding implicitly means completion of negotiation. See
     * <https://mail.jabber.org/pipermail/jdev/2017-August/090324.html> */
    getEventStream().ofType(FeatureNegotiatedEvent.class).filter(it ->
        it.getFeature() == StreamFeature.RESOURCE_BINDING
            || it.getFeature() == StreamFeature.RESOURCE_BINDING_2
    ).filter(
        it -> checkIfAllMandatoryFeaturesNegotiated()
    ).observeOn(Schedulers.io()).subscribe(it -> state.change(State.COMPLETED));

    if (session.getConnection().getTlsMethod() == Connection.TlsMethod.STARTTLS) {
      final Disposable subscription = session.getEventStream().ofType(
          StandardSession.TlsDeployedEvent.class
      ).firstElement().observeOn(Schedulers.io()).subscribe(it -> {
        if (it.getError() == null) {
          sendStreamOpening();
        } else {
          handshakeResult.onError(it.getError());
        }
      });
      bin.add(subscription);
    }
    bin.add(
        session
            .stateProperty()
            .getStream()
            .filter(it -> it == Session.State.DISCONNECTED)
            .subscribe(it -> state.change(State.STREAM_CLOSED))
    );
    this.pipeline = pipeline;
    try {
      pipeline.stateProperty().getAndDo(state -> {
        if (pipeline.stateProperty().get() == Pipeline.State.STOPPED) {
          bin.add(pipeline
              .stateProperty()
              .getStream()
              .filter(it -> it == Pipeline.State.RUNNING)
              .firstElement()
              .observeOn(Schedulers.io())
              .subscribe(event -> start()));
        } else {
          start();
        }
      });
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public void onRemovedFromPipeline(final Pipeline<?, ?> pipeline) {
    state.change(State.DISPOSED);
    bin.dispose();
  }

  @Override
  public void onWriting(Pipeline<?, ?> pipeline,
                        Object toWrite,
                        List<Object> toForward) throws Exception {
    if (state.get() == State.DISPOSED) {
      throw new IllegalStateException();
    }
    if (!(toWrite instanceof Document)) {
      super.onWriting(pipeline, toWrite, toForward);
    } else if (state.get() == State.INITIALIZED
        || state.get() == State.STREAM_CLOSED) {
    } else {
      super.onWriting(pipeline, toWrite, toForward);
    }
  }
}