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

import chat.viska.commons.pipelines.BlankPipe;
import chat.viska.commons.pipelines.Pipeline;
import chat.viska.sasl.AuthenticationException;
import chat.viska.sasl.Client;
import chat.viska.sasl.ClientFactory;
import chat.viska.sasl.PropertyRetriever;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * For handling handshaking, login and management of an XMPP stream.
 */
public class HandshakerPipe extends BlankPipe implements SessionAware {

  /**
   * Indicates the state of a {@link HandshakerPipe}.
   */
  public enum State {

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
     */
    DISPOSED
  }

  private static final Version SUPPORTED_VERSION = new Version(1, 0);

  private final Subject<EventObject> eventStream = PublishSubject.create();
  private final DocumentBuilder domBuilder;
  private final Session session;
  private final List<StreamFeature> streamFeaturesOrder = new ArrayList<>(
      Arrays.asList(StreamFeature.getRecommendedNeogtiationOrder())
  );
  private final Set<StreamFeature> negotiatedFeatures = new HashSet<>();
  private final String authnId;
  private final String authzId;
  private final PropertyRetriever retriever;
  private final Base64 base64 = new Base64(0, new byte[0], false);
  private Pipeline<?, ?> pipeline;
  private Client saslCient;
  private String resource = "";
  private String streamId = "";
  private StreamFeature negotiatingFeature;
  private State state = State.STREAM_CLOSED;
  private Disposable pipelineStartedSubscription;
  private StreamErrorException serverStreamError;
  private StreamErrorException clientStreamError;

  private String[] convertToMechanismArray(NodeList nodes) {
    int cursor = 0;
    final String[] mechanisms = new String[nodes.getLength()];
    while (cursor < nodes.getLength()) {
      mechanisms[cursor] = nodes.item(cursor).getTextContent();
      ++cursor;
    }
    return mechanisms;
  }

  private StreamErrorException
  convertToStreamErrorException(@NonNull final Document document) {
    StreamErrorException.Condition condition = null;
    Element conditionElement = null;
    int cursor = 0;
    final NodeList nodes = document.getDocumentElement().getChildNodes();
    while (cursor < nodes.getLength()) {
      conditionElement = (Element) nodes.item(cursor);
      condition = StreamErrorException.Condition.of(conditionElement.getLocalName());
      if (condition != null) {
        break;
      } else {
        ++cursor;
      }
    }
    if (condition != null) {
      NodeList textNodes = conditionElement.getElementsByTagNameNS(
          CommonXmlns.STREAM_CONTENT, "text"
      );
      if (textNodes.getLength() > 0) {
        return new StreamErrorException(
            condition,
            textNodes.item(0).getTextContent()
        );
      } else {
        return new StreamErrorException(condition);
      }
    } else if (document.getDocumentElement().hasChildNodes()){
      return new StreamErrorException(
          StreamErrorException.Condition.UNDEFINED_CONDITION,
          String.format(
              "[UNRECOGNIZED ERROR: %1s]",
              document.getDocumentElement().getFirstChild().getLocalName()
          )
      );
    } else {
      return new StreamErrorException(
          StreamErrorException.Condition.UNDEFINED_CONDITION,
          "[NO ERROR CONDITION SPECIFIED]"
      );
    }
  }

  private boolean checkIfAllMandatoryFeaturesNegotiated() {
    List<StreamFeature> notNegotiated = new ArrayList<>(streamFeaturesOrder);
    notNegotiated.removeAll(negotiatedFeatures);
    for (StreamFeature feature : notNegotiated) {
      if (feature.isMandatory()) {
        return false;
      }
    }
    return true;
  }

  private void sendXml(@NonNull final String xml) {
    try {
      pipeline.write(domBuilder.parse(
          new InputSource(new StringReader(xml))
      ));
    } catch (IOException | SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void sendStreamOpening() {
    sendXml(String.format(
        "<open xmlns=\"%1s\" to=\"%2s\" version=\"1.0\"/>",
        CommonXmlns.STREAM_OPENING_WEBSOCKET,
        session.getConnection().getDomain()
    ));
  }

  private void sendStreamClosing() {
    sendXml(String.format(
        "<close xmlns=\"%1s\"/>",
        CommonXmlns.STREAM_OPENING_WEBSOCKET
    ));
  }

  private void sendStreamError(@NonNull final StreamErrorException error) {
    final Document document;
    try {
      document = domBuilder.parse(new InputSource(new StringReader(String.format(
          "<error xmlns=\"%1s\"><%2s xmlns=\"%3s\"/></error>",
          CommonXmlns.STREAM_HEADER,
          error.getCondition().toString(),
          CommonXmlns.STREAM_CONTENT
      ))));
    } catch (SAXException | IOException ex) {
      throw new RuntimeException(ex);
    }
    if (error.getMessage() != null) {
      final Element textElement = document.createElementNS(
          CommonXmlns.STREAM_CONTENT,
          "text"
      );
      textElement.setTextContent(error.getMessage());
      document.getDocumentElement().appendChild(textElement);
    }
    pipeline.write(document);
    this.clientStreamError = error;
    closeStream();
  }

  private void consumeStreamOpening(@NonNull final Document document)
      throws StreamErrorException {
    Objects.requireNonNull(document);
    Version serverVersion = null;
    final String serverVersionText = document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_OPENING_WEBSOCKET,
        "version"
    );
    try {
      serverVersion = new Version(serverVersionText);
    } catch (Exception ex) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_VERSION,
          serverVersionText,
          ex
      ));
    }
    if (!SUPPORTED_VERSION.equals(serverVersion)) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_VERSION,
          serverVersionText
      ));
    }
    final String serverDomain = document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_OPENING_WEBSOCKET,
        "from"
    );
    if (!serverDomain.equals(session.getConnection().getDomain())) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.INVALID_FROM,
          serverDomain
      ));
    }
    final String streamId = document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_OPENING_WEBSOCKET,
        "id"
    );
    if (streamId.trim().isEmpty()) {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.INVALID_XML,
          "Empty stream ID"
      ));
    }
    this.streamId = streamId;
  }

  private Element consumeStreamFeatures(@NonNull final Document document) {
    final NodeList serverFeatures = document.getDocumentElement().getChildNodes();
    if (serverFeatures.getLength() == 0) {
      return null;
    }

    for (StreamFeature it : this.streamFeaturesOrder) {
      int cursor = 0;
      while (cursor < serverFeatures.getLength()) {
        final Element currentFeature = (Element) serverFeatures.item(cursor);
        if (it.getName().equals(currentFeature.getLocalName())
            && it.getNamespace().equals(currentFeature.getNamespaceURI())) {
          this.negotiatingFeature = it;
          return currentFeature;
        }
        ++cursor;
      }
    }

    return null;
  }

  private void initializeSaslNegotiation(@NonNull final Element featureElement) {
    this.saslCient = new ClientFactory(this.session.getSaslMechanisms()).newClient(
        convertToMechanismArray(featureElement.getChildNodes()),
        this.authnId,
        this.authzId,
        this.retriever
    );
    if (this.saslCient == null) {
      sendXml(String.format(
          "<abort xmlns=\"%1s\"/>",
          CommonXmlns.SASL
      ));
      return;
    }
    String msg = "";
    if (this.saslCient.isClientFirst()) {
      msg = this.base64.encodeToString(this.saslCient.respond());
      if (msg.isEmpty()) {
        msg = "=";
      }
    }
    sendXml(String.format(
        "<auth xmlns=\"%1s\" mechanism=\"%2s\">%3s</auth>",
        CommonXmlns.SASL,
        this.saslCient.getMechanism(),
        msg
    ));
  }

  private void consumeStartTls(@NonNull final Document document) {
    throw new UnsupportedOperationException();
  }

  private void consumeSasl(@NonNull final Document document)
      throws AuthenticationException {
    final String rootName = document.getDocumentElement().getLocalName();
    final String text = document.getDocumentElement().getTextContent();

    if ("failure".equals(rootName)) {
      this.negotiatedFeatures.remove(StreamFeature.SASL);
      this.negotiatingFeature = null;
      throw new AuthenticationException(
          AuthenticationException.Condition.CLIENT_NOT_AUTHORIZED
      );
    } else if ("success".equals(rootName) && text.isEmpty()) {
      this.negotiatedFeatures.add(StreamFeature.SASL);
      this.negotiatingFeature = null;
    } else if ("success".equals(rootName) || "challenge".equals(rootName)) {
      this.saslCient.acceptChallenge(this.base64.decode(text));
      if (!this.saslCient.isCompleted()) {
        final byte[] response = this.saslCient.respond();
        if (response != null) {
          sendXml(String.format(
              "<response xmlns=\"%1s\">%2s</response>",
              CommonXmlns.SASL,
              this.base64.encodeToString(response)
          ));
        } else {
          sendXml(String.format(
              "<abort xmlns=\"%1s\"/>",
              CommonXmlns.SASL
          ));
          closeStream();
          throw this.saslCient.getError();
        }
      }
    } else {
      sendStreamError(new StreamErrorException(
          StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE
      ));
    }
  }

  private void consumeStreamCompression(@NonNull final Document document) {
    throw new UnsupportedOperationException();
  }

  private void consumeResourceBinding(@NonNull final Document document) {
    throw new UnsupportedOperationException();
  }

  private void setState(final State state) {
    final State oldState = this.state;
    this.state = state;
    eventStream.onNext(new PropertyChangeEvent(this, "State", oldState, state));
  }

  public HandshakerPipe(@NonNull final Session session,
                        @NonNull final String authnId,
                        @Nullable final String authzId,
                        @NonNull final PropertyRetriever retriever) {
    Objects.requireNonNull(session, "`session` is absent.");
    Validate.notEmpty(authnId, "`authnId` is absent.");
    Validate.notEmpty(authzId, "`authzId` is absent.");
    Objects.requireNonNull(retriever, "`retriever` is absent.");
    this.session = session;
    this.authnId = authnId;
    this.authzId = authzId;
    this.retriever = retriever;

    // Initializing DOM builder
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      domBuilder = builderFactory.newDocumentBuilder();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    }

    // Preprocessing stream features list
    if (!this.session.getConnection().isStartTlsRequired()) {
      this.streamFeaturesOrder.remove(StreamFeature.STARTTLS);
    }
    if (this.session.getStreamCompression() == null) {
      this.streamFeaturesOrder.remove(StreamFeature.STREAM_COMPRESSION);
    }
  }

  public synchronized void closeStream() {
    switch (state) {
      case STREAM_CLOSING:
        return;
      case STREAM_CLOSED:
        return;
      case DISPOSED:
        throw new IllegalStateException("The pipe has been disposed.");
      default:
        break;
    }
    setState(State.STREAM_CLOSING);
    sendStreamClosing();
  }

  @NonNull
  public String getResource() {
    return resource;
  }

  /**
   * Gets a stream of emitted {@link EventObject}s. It never emits any errors
   * but will emit a completion when this class is disposed of.
   *
   * <p>This class emits only the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   *   <li>
   *     {@link java.beans.PropertyChangeEvent}
   *     <ul>
   *       <li>{@code State}</li>
   *       <li>{@code Resource}</li>
   *     </ul>
   *   </li>
   * </ul>
   */
  @NonNull
  public Observable<EventObject> getEventStream() {
    return eventStream;
  }

  @NonNull
  public synchronized String getStreamId() {
    return streamId;
  }

  /**
   * Gets the current {@link State} of the class.
   */
  @NonNull
  public State getState() {
    return state;
  }

  @Nullable
  public StreamErrorException getServerStreamError() {
    return serverStreamError;
  }

  @Nullable
  public StreamErrorException getClientStreamError() {
    return clientStreamError;
  }

  /**
   * Invoked when the Pipe is reading data.
   * @throws AuthenticationException If a failure occurred during an SASL
   *         negotiation.
   */
  @Override
  public synchronized void onReading(final Pipeline<?, ?> pipeline,
                                     final Object toRead,
                                     final List<Object> toForward)
      throws Exception {
    if (this.state == State.DISPOSED) {
      throw new IllegalStateException(
          "Cannot invoke `onReading()` of a disposed Pipe."
      );
    }

    final Document document;
    if (toRead instanceof Document) {
      document = (Document) toRead;
    } else {
      document = null;
    }
    if (document == null) {
      toForward.add(toRead);
      return;
    }

    if (this.state == State.STREAM_CLOSED) {
      return;
    }

    final String rootName = document.getDocumentElement().getLocalName();
    final String rootNs = document.getDocumentElement().getNamespaceURI();

    if ("open".equals(rootName) && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
      switch (state) {
        case STARTED:
          try {
            consumeStreamOpening(document);
          } catch (StreamErrorException ex) {
            sendStreamError(ex);
            return;
          }
          setState(State.NEGOTIATING);
          return;
        case NEGOTIATING:
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.UNDEFINED_CONDITION,
              "Expecting stream features but a stream opening was received."
          ));
        case COMPLETED:
          sendStreamError(new StreamErrorException(
              StreamErrorException.Condition.UNDEFINED_CONDITION,
              "Server unexpectedly restarted the stream."
          ));
        case STREAM_CLOSING:
          return;
        default:
          break;
      }
    } else if ("close".equals(rootName) && CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
      switch (state) {
        case STREAM_CLOSING:
          setState(State.STREAM_CLOSED);
          pipeline.clear();
          return;
        default:
          sendStreamClosing();
          setState(State.STREAM_CLOSING);
          break;
      }
    } else if ("features".equals(rootName) && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
      if (state == State.NEGOTIATING) {
        final Element selectedFeature = consumeStreamFeatures(document);
        if (selectedFeature == null) {
          if (checkIfAllMandatoryFeaturesNegotiated()) {
            setState(State.COMPLETED);
          } else {
            sendStreamError(new StreamErrorException(
                StreamErrorException.Condition.UNSUPPORTED_FEATURE
            ));
          }
        } else {
          switch (negotiatingFeature) {
            case SASL:
              initializeSaslNegotiation(selectedFeature);
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
            StreamErrorException.Condition.UNDEFINED_CONDITION,
            "Not negotiating stream features."
        ));
      }
    } else if (CommonXmlns.SASL.equals(rootNs)) {
      if (state == State.NEGOTIATING && negotiatingFeature == StreamFeature.SASL) {
        consumeSasl(document);
      } else {
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNDEFINED_CONDITION,
            "Not negotiating SASL."
        ));
      }
    } else if ("error".equals(rootName) && CommonXmlns.STREAM_HEADER.equals(rootNs)) {
      closeStream();
      this.serverStreamError = convertToStreamErrorException(document);
    } else {
      if (state == State.COMPLETED || state == State.STREAM_CLOSING) {
        toForward.add(toRead);
      } else {
        sendStreamError(new StreamErrorException(
            StreamErrorException.Condition.UNSUPPORTED_STANZA_TYPE,
            "Unexpected data received, expecting a stream opening."
        ));
      }
    }
  }

  @Override
  public synchronized void onAddedToPipeline(final Pipeline<?, ?> pipeline) {
    if (this.pipeline != null || state == State.DISPOSED) {
      throw new IllegalStateException();
    }
    this.pipeline = pipeline;
    final Pipeline.State pipelineState = pipeline.getState();
    if (pipelineState == Pipeline.State.INITIALIZED || pipelineState == Pipeline.State.STOPPED) {
      pipelineStartedSubscription = pipeline.getEventStream()
          .ofType(PropertyChangeEvent.class)
          .filter(new Predicate<PropertyChangeEvent>() {
            @Override
            public boolean test(PropertyChangeEvent event) throws Exception {
              return event.getPropertyName().equals("State")
                  && event.getNewValue() == Pipeline.State.RUNNING;
            }
          }).firstElement()
          .subscribe(new Consumer<PropertyChangeEvent>() {
            @Override
            public void accept(PropertyChangeEvent event) throws Exception {
              setState(State.STARTED);
              sendStreamOpening();
            }
          });
    } else {
      setState(State.STARTED);
      sendStreamOpening();
    }
  }

  @Override
  public synchronized void onRemovedFromPipeline(final Pipeline<?, ?> pipeline) {
    setState(State.DISPOSED);
    pipelineStartedSubscription.dispose();
  }

  @Override
  public Session getSession() {
    return session;
  }
}