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

import chat.viska.commons.Base64Codec;
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
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.BehaviorProcessor;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.beans.PropertyChangeEvent;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * For handling handshaking, login and management of an XMPP stream.
 *
 * <h2>Usage</h2>
 *
 * <p>The handshake starts once the {@link Pipeline} starts running or it is
 * already running when this Pipe is added to a Pipeline. In order to get
 * get notified once the handshake/login completes, subscribe to a
 * {@link PropertyChangeEvent} in which {@code State} has changed to
 * {@link State#COMPLETED}. In order to check the cause of a failed handshake
 * or an abnormally closed XMPP stream, check {@link #getError()}.</p>
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
public class HandshakerPipe extends BlankPipe implements SessionAware {

  /**
   * Indicates the state of a {@link HandshakerPipe}.
   */
  public enum State {

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

    public FeatureNegotiatedEvent(@Nonnull final HandshakerPipe source,
                                  @Nonnull final StreamFeature feature) {
      super(source);
      Objects.requireNonNull(feature);
      this.feature = feature;
    }

    /**
     * Gets the {@link StreamFeature} that was negotiated.
     */
    @Nonnull
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
  private final BehaviorProcessor<State> state = BehaviorProcessor.createDefault(State.INITIALIZED);
  private final StandardSession session;
  private final Set<StreamFeature> negotiatedFeatures = new HashSet<>();
  private final Jid loginJid;
  private final Jid authzId;
  private final CredentialRetriever retriever;
  private final Base64Codec base64;
  private final String presetResource;
  private final List<String> saslMechanisms = new ArrayList<>();
  private final FlowableProcessor<EventObject> eventStream;
  private Exception error;
  private Pipeline<?, ?> pipeline;
  private Client saslClient;
  private StreamFeature negotiatingFeature;
  private Disposable pipelineStartedSubscription;
  private Jid negotiatedJid = Jid.EMPTY;
  private String resourceBindingIqId = "";

  private void changeState(@Nonnull final State state) {
    synchronized (this.state) {
      if (this.state.getValue() != state) {
        this.state.onNext(state);
      }
    }
  }

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

  private void consumeStreamOpening(@Nonnull final Document document) {
    Objects.requireNonNull(document);
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
  private Element consumeStreamFeatures(@Nonnull final Document document) {
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

  private void initiateSasl(@Nonnull final Element mechanismsElement)
      throws SAXException {
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
      msg = this.base64.encode(this.saslClient.respond());
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
    this.resourceBindingIqId = UUID.randomUUID().toString();
    final Document iq = Stanza.getIqTemplate(
        Stanza.IqType.SET,
        resourceBindingIqId,
        null,
        null
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

  private void consumeStartTls(@Nonnull final Document xml) {
    switch (xml.getDocumentElement().getLocalName()) {
      case "proceed":
        this.negotiatedFeatures.add(StreamFeature.STARTTLS);
        this.eventStream.onNext(
            new FeatureNegotiatedEvent(this, StreamFeature.STARTTLS)
        );
        this.negotiatingFeature = null;
        break;
      case "failure":
        this.error = new Exception("Server failed to proceed StartTLS.");
        break;
      default:
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
        ));
    }
  }

  private void consumeSasl(@Nonnull final Document document) throws SAXException {
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
        this.error = new AuthenticationException(
            AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED
        );
        break;
      case "success":
        if (StringUtils.isNotBlank(msg)) {
          this.saslClient.acceptChallenge(this.base64.decode(msg));
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
        this.saslClient.acceptChallenge(this.base64.decode(msg));
        if (!this.saslClient.isCompleted()) {
          final byte[] response = this.saslClient.respond();
          if (response != null) {
            this.pipeline.write(DomUtils.readDocument(String.format(
                "<response xmlns=\"%1s\">%2s</response>",
                CommonXmlns.SASL,
                this.base64.encode(response)
            )));
          } else {
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

  private void consumeStreamCompression(@Nonnull Document document) {
    throw new UnsupportedOperationException();
  }

  private void consumeResourceBinding(@Nonnull final Document document)
      throws SAXException {
    if (!this.resourceBindingIqId.equals(document.getDocumentElement().getAttribute("id"))) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.NOT_AUTHORIZED
      ));
    } else if ("error".equals(document.getDocumentElement().getAttribute("type"))) {
      try {
        this.error = StanzaErrorException.fromXml(document);
      } catch (StreamErrorException ex) {
        sendStreamError(ex);
      }
    } else if ("result".equals(document.getDocumentElement().getAttribute("type"))) {
      final Element bindElement = (Element) document
          .getDocumentElement()
          .getElementsByTagNameNS(CommonXmlns.RESOURCE_BINDING, "bind")
          .item(0);
      final String[] results = bindElement
          .getElementsByTagNameNS(CommonXmlns.RESOURCE_BINDING, "jid")
          .item(0)
          .getTextContent()
          .split(" ");
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
    if (this.state.getValue() != State.INITIALIZED) {
      throw new IllegalStateException();
    }
    changeState(State.STARTED);
    sendStreamOpening();
  }

  /**
   * Default constructor.
   * @param session Associated XMPP session.
   * @param loginJid Authentication ID, which is typically the local part of a
   *        {@link Jid} for
   *        <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>
   *        mechanisms which uses a "simple user name".
   * @param authzId Authorization ID, which is a bare {@link Jid}.
   * @param retriever Credential retriever.
   * @param saslMechanisms <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a>
   *        Mechanisms used during handshake. Use {@code null} to specify the
   *        default ones.
   * @param resource XMPP Resource. If {@code null} or empty, the server will
   *                 generate a random one on behalf of the client.
   * @param registering Indicates if the handshake includes in-band
   *                    registration.
   */
   public HandshakerPipe(@Nonnull final StandardSession session,
                         @Nullable final Jid loginJid,
                         @Nullable final Jid authzId,
                         @Nullable final CredentialRetriever retriever,
                         @Nullable final List<String> saslMechanisms,
                         @Nullable final String resource,
                         final boolean registering) {
    if (registering) {
      Validate.isTrue(!Jid.isEmpty(loginJid), "loginJid");
    }
    if (!Jid.isEmpty(loginJid)) {
      Objects.requireNonNull(retriever, "retriever");
    }
    this.session = session;
    this.loginJid = Jid.isEmpty(loginJid) ? Jid.EMPTY : loginJid;
    this.authzId = Jid.isEmpty(authzId) ? Jid.EMPTY : authzId;
    this.retriever = retriever;
    if (saslMechanisms == null || saslMechanisms.isEmpty()) {
      this.saslMechanisms.add("SCRAM-SHA-1");
    } else {
      this.saslMechanisms.addAll(saslMechanisms);
    }
    this.presetResource = resource == null ? "" : resource;
    try {
      this.base64 = Base64Codec.getInstance();
    } catch (NoSuchProviderException ex) {
      throw new RuntimeException(ex);
    }

    final FlowableProcessor<EventObject> unsafeEventStream = PublishProcessor.create();
    this.eventStream = unsafeEventStream.toSerialized();
  }

  /**
   * Closes the XMPP stream.
   */
  public Completable closeStream() {
    synchronized (state) {
      switch (state.getValue()) {
        case INITIALIZED:
          changeState(State.STREAM_CLOSED);
          return Completable.complete();
        case STREAM_CLOSING:
          break;
        case STREAM_CLOSED:
          return Completable.complete();
        case DISPOSED:
          return Completable.complete();
        default:
          //TODO: Timeout
          sendStreamClosing();
          changeState(State.STREAM_CLOSING);
          break;
      }
    }
    return state.filter(it -> it == State.STREAM_CLOSED).first(State.STREAM_CLOSED).toCompletable();
  }

  public void sendStreamError(@Nonnull final StreamErrorException error) {
    if (session.getState().getValue() == Session.State.ONLINE
        || session.getState().getValue() == Session.State.HANDSHAKING) {
      pipeline.write(error.toXml());
    }
    this.error = error;
    closeStream();
  }

  /**
   * Gets the JID negotiated during Resource Binding.
   * @return {@code null} if the negotiation is not completed yet.
   */
  @Nonnull
  public Jid getNegotiatedJid() {
    return negotiatedJid;
  }

  /**
   * Gets the current {@link State} of this class.
   */
  @Nonnull
  public FlowableProcessor<State> getState() {
    return state;
  }

  /**
   * Gets the error occured during a handshake.
   * @return {@code null} if the handshake is not completed yet or it was
   *         successful.
   */
  public Exception getError() {
    return error;
  }

  @Nonnull
  public Set<StreamFeature> getStreamFeatures() {
    return Collections.unmodifiableSet(negotiatedFeatures);
  }

  @Nonnull
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
                        final List<Object> toForward)
      throws Exception {
    synchronized (this.state) {
      if (this.state.getValue() == State.DISPOSED) {
        throw new IllegalStateException("Pipe disposed.");
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

      if (this.state.getValue() == State.STREAM_CLOSED
          || this.state.getValue() == State.INITIALIZED) {
        return;
      }

      final String rootName = document.getDocumentElement().getLocalName();
      final String rootNs = document.getDocumentElement().getNamespaceURI();

      if ("open".equals(rootName)
          && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
        switch (this.state.getValue()) {
          case STARTED:
            consumeStreamOpening(document);
            changeState(State.NEGOTIATING);
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
      } else if ("close".equals(rootName)
          && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
        switch (this.state.getValue()) {
          case STREAM_CLOSING:
            break;
          default:
            sendStreamClosing();
            break;
        }
        changeState(State.STREAM_CLOSED);
      } else if ("features".equals(rootName)
          && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
        if (this.state.getValue() == State.NEGOTIATING) {
          final Element selectedFeature = consumeStreamFeatures(document);
          if (selectedFeature == null) {
            if (checkIfAllMandatoryFeaturesNegotiated()) {
              changeState(State.COMPLETED);
            } else {
              sendStreamError(new StreamErrorException(
                  StreamErrorException.Condition.UNSUPPORTED_FEATURE,
                  "We have mandatory features that you do not support."
              ));
            }
          } else {
            switch (negotiatingFeature) {
              case STARTTLS:
                this.session.getLogger().fine("Negotiating StartTLS.");
                initiateStartTls();
                break;
              case SASL:
                this.session.getLogger().fine("Negotiating SASL.");
                initiateSasl(selectedFeature);
                break;
              case RESOURCE_BINDING:
                this.session.getLogger().fine("Negotiating Resource Binding.");
                initiateResourceBinding();
                break;
              default:
                sendStreamError(new StreamErrorException(
                    StreamErrorException.Condition.UNSUPPORTED_FEATURE
                ));
                break;
            }
          }
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Re-negotiating features not allowed."
          ));
        }
      } else if (CommonXmlns.STARTTLS.equals(rootNs)) {
        if (this.state.getValue() == State.NEGOTIATING
            && this.negotiatingFeature == StreamFeature.STARTTLS) {
          consumeStartTls(document);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Not negotiating StartTLS at the time."
          ));
        }
      } else if (CommonXmlns.SASL.equals(rootNs)) {
        if (this.state.getValue() == State.NEGOTIATING
            && negotiatingFeature == StreamFeature.SASL) {
          consumeSasl(document);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.POLICY_VIOLATION,
              "Not negotiating SASL at the time."
          ));
        }
      } else if ("iq".equals(rootName)) {
        if (this.state.getValue() == State.NEGOTIATING
            && this.negotiatingFeature == StreamFeature.RESOURCE_BINDING) {
          consumeResourceBinding(document);
        } else if (this.state.getValue() == State.COMPLETED) {
          super.onReading(pipeline, toRead, toForward);
        } else {
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.NOT_AUTHORIZED,
              "Stanzas not allowed before stream negotiation completes."
          ));
        }
      } else if ("error".equals(rootName)
          && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
        this.error = StreamErrorException.fromXml(document);
        closeStream();
      } else {
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
        ));
      }
    }
    if (this.error != null) {
      closeStream();
    }
  }

  @Override
  public void onAddedToPipeline(final Pipeline<?, ?> pipeline) {
    // TODO: Support for stream resumption
    if (this.state.getValue() != State.INITIALIZED) {
      throw new IllegalStateException();
    }

    /* Resource Binding implicitly means completion of negotiation. See
     * <https://mail.jabber.org/pipermail/jdev/2017-August/090324.html> */
    getEventStream().ofType(FeatureNegotiatedEvent.class).filter(it ->
        it.getFeature() == StreamFeature.RESOURCE_BINDING
            || it.getFeature() == StreamFeature.RESOURCE_BINDING_2
    ).filter(
        it -> checkIfAllMandatoryFeaturesNegotiated()
    ).observeOn(Schedulers.io()).subscribe(it -> changeState(State.COMPLETED));

    if (this.session.getConnection().getTlsMethod() == Connection.TlsMethod.STARTTLS) {
      this.session.getEventStream().ofType(
          StandardSession.StartTlsDeployedEvent.class
      ).observeOn(Schedulers.io()).subscribe( it -> {
        sendStreamOpening();
      });
    }
    this.session.getEventStream().ofType(
        StandardSession.ConnectionTerminatedEvent.class
    ).observeOn(Schedulers.io()).subscribe(it -> changeState(State.STREAM_CLOSED));

    this.pipeline = pipeline;
    if (pipeline.getState().getValue() == Pipeline.State.STOPPED) {
      pipelineStartedSubscription = pipeline.getState().getStream()
          .filter(it -> it == Pipeline.State.RUNNING)
          .firstElement()
          .observeOn(Schedulers.io())
          .subscribe(event -> start());
    } else {
      start();
    }
  }

  @Override
  public void onRemovedFromPipeline(final Pipeline<?, ?> pipeline) {
    changeState(State.DISPOSED);
    pipelineStartedSubscription.dispose();
  }

  @Override
  public void onWriting(Pipeline<?, ?> pipeline,
                        Object toWrite,
                        List<Object> toForward) throws Exception {
    if (this.state.getValue() == State.DISPOSED) {
      throw new IllegalStateException();
    }
    if (!(toWrite instanceof Document)) {
      super.onWriting(pipeline, toWrite, toForward);
    } else if (this.state.getValue() == State.INITIALIZED
        || this.state.getValue() == State.STREAM_CLOSED) {
    } else {
      super.onWriting(pipeline, toWrite, toForward);
    }
  }

  @Override
  @Nonnull
  public StandardSession getSession() {
    return session;
  }
}