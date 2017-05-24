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
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import io.reactivex.subjects.PublishSubject;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class HandshakerPipe extends BlankPipe implements SessionAware {

  private enum State {

    /**
     * Indicates the Pipe has just been created.
     */
    INITIALIZED,

    /**
     * Indicates a stream opening has been sent and awaiting a stream opening
     * from the server.
     */
    STARTED,

    /**
     * Indicates negotiation of stream features, authentication and other
     * handshake process is happening.
     */
    NEGOTIATING,

    /**
     * Indicates the handshake is completed.
     */
    COMPLETED,

    /**
     * Indicates a stream closing has been issued and awaiting for a closing
     * confirmation from the server.
     */
    STREAM_CLOSING,

    /**
     * Indicates the 2 parties has exchanged stream closings and the stream is
     * closed.
     */
    STREAM_CLOSED,

    /**
     * Indicates the handshaker has been removed from a {@link Pipeline} or the
     * {@link Pipeline} has been disposed.
     */
    DISPOSED
  }

  private enum NegotiationType {
    SASL,
    STARTTLS,
    STREAM_FEATURES
  }

  private static final AtomicReference<DocumentBuilder> DOM_BUILDER_INSTANCE;
  private static final Version SUPPORTED_VERSION = new Version(1, 0);

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

  private final PublishSubject<EventObject> eventStream = PublishSubject.create();
  private final DefaultSession session;
  private String resource = "";
  private Pipeline pipeline;
  private String streamId;
  private AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);
  private Disposable pipelineStartedSubscription;

  private void sendXml(final @NonNull String xml) throws SAXException {
    try {
      pipeline.write(DOM_BUILDER_INSTANCE.get().parse(
          new InputSource(new StringReader(xml))
      ));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void sendStreamOpening() {
    try {
      sendXml(String.format(
          "<open xmlns=\"%1s\" to=\"%2s\" version=\"1.0\"/>",
          CommonXmlns.STREAM_HEADER_WEBSOCKET,
          session.getConnection().getDomain()
      ));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
  }

  private void sendStreamClosing() {
    try {
      sendXml(String.format(
          "<close xmlns=\"%1s\"/>",
          CommonXmlns.STREAM_HEADER_WEBSOCKET
      ));
    } catch (SAXException ex) {
      throw new RuntimeException(ex);
    }
    setState(State.STREAM_CLOSING);
  }

  private void consumeStreamOpening(final Document document) throws HandshakeException {
    final Version version = new Version(document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_HEADER_WEBSOCKET,
        "version"
    ));
    if (!SUPPORTED_VERSION.equals(version)) {
      throw new HandshakeException(String.format(
          "XMPP version %1s is unsupported.",
          version
      ));
    }
    final String serverDomain = document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_HEADER_WEBSOCKET,
        "from"
    );
    if (!serverDomain.equals(session.getConnection().getDomain())) {
      throw new HandshakeException(
          "Server domain `" + serverDomain + "` does not match."
      );
    }
    final String streamId = document.getDocumentElement().getAttributeNS(
        CommonXmlns.STREAM_HEADER_WEBSOCKET,
        "id"
    );
    if (streamId.trim().isEmpty()) {
      throw new HandshakeException("Empty stream ID");
    }
    this.streamId = streamId;
  }

  private void setState(State state) {
    State oldState = this.state.getAndSet(state);
    eventStream.onNext(new PropertyChangeEvent(this, "State", oldState, state));
  }

  private boolean isStartTlsRequired() {
    final Connection method = session.getConnection();
    return method.isTlsEnabled() && method.getProtocol() == Connection.Protocol.TCP;
  }

  public HandshakerPipe(final @NonNull DefaultSession session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  public void closeStream() {
    switch (state.get()) {
      case INITIALIZED:
        throw new IllegalStateException(
            "Cannot close a stream that is not yet started."
        );
      case STREAM_CLOSING:
        return;
      case STREAM_CLOSED:
        return;
      case DISPOSED:
        throw new IllegalStateException("The pipe has been disposed.");
      default:
        break;
    }
    sendStreamClosing();
  }

  public void sendStreamError() {

  }

  @NonNull
  public String getResource() {
    return resource;
  }

  @NonNull
  public Observable<EventObject> getEventStream() {
    return eventStream;
  }

  @NonNull
  public String getStreamId() {
    return streamId;
  }

  @NonNull
  public State getState() {
    return state.get();
  }

  @Override
  public void onReading(Pipeline<?, ?> pipeline, Object toRead, List<Object> toForward)
      throws Exception {
    switch (state.get()) {
      case INITIALIZED:
        toForward.add(toRead);
        return;
      case STREAM_CLOSED:
        return;
      case DISPOSED:
        throw new IllegalStateException();
      default:
        break;
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

    final String rootName = document.getDocumentElement().getLocalName();
    final String rootNs = document.getDocumentElement().getNamespaceURI();

    if (rootName.equals("open") && rootNs.equals(CommonXmlns.STREAM_HEADER_WEBSOCKET)) {
      switch (state.get()) {
        case STARTED:
          consumeStreamOpening(document);
          return;
        case NEGOTIATING:
          sendStreamClosing();
          throw new HandshakeException(
              "Unexpected data received, expecting stream features."
          );
        case COMPLETED:
          sendStreamClosing();
          throw new HandshakeException(
              "Server unexpectedly restarted the stream."
          );
        case STREAM_CLOSING:
          return;
        default:
          break;
      }
    } else if (rootName.equals("close") && rootNs.equals(CommonXmlns.STREAM_HEADER_WEBSOCKET)) {
      switch (state.get()) {
        case STREAM_CLOSING:
          setState(State.STREAM_CLOSED);
          pipeline.clear();
          return;
        default:
          sendStreamClosing();
          setState(State.STREAM_CLOSING);
          return;
      }
    } else if (rootName.equals("features") && rootNs.equals(CommonXmlns.STREAM_HEADER_TCP)) {

    } else {
      if (state.get() == State.COMPLETED || state.get() == State.STREAM_CLOSING) {
        toForward.add(toRead);
      } else {
        sendStreamClosing();
        throw new HandshakeException(
            "Unexpected data received, expecting a stream opening."
        );
      }
    }

  }

  @Override
  public void onAddedToPipeline(final Pipeline<?, ?> pipeline) {
    if (this.pipeline != null || state.get() == State.DISPOSED) {
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
              sendStreamOpening();
              setState(State.STARTED);
            }
          });
    } else {
      sendStreamOpening();
      setState(State.STARTED);
    }
  }

  @Override
  public void onRemovedFromPipeline(final Pipeline<?, ?> pipeline) {
    pipelineStartedSubscription.dispose();
    setState(State.DISPOSED);
  }

  @Override
  public DefaultSession getSession() {
    return session;
  }
}