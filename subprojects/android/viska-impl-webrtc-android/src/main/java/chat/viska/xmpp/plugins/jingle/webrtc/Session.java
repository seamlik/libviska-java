/*
 * Copyright 2018 Kai-Chung Yan (殷啟聰)
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

package chat.viska.xmpp.plugins.jingle.webrtc;

import chat.viska.xmpp.Jid;
import chat.viska.xmpp.plugins.jingle.Content;
import chat.viska.xmpp.plugins.jingle.ContentGroup;
import chat.viska.xmpp.plugins.jingle.RtpContent;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.CompletableSubject;
import io.reactivex.subjects.SingleSubject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.concurrent.ThreadSafe;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SessionDescription;
import rxbeans.Property;
import rxbeans.StandardProperty;

/**
 * {@link chat.viska.xmpp.plugins.jingle.Session} backed by WebRTC.
 */
@ThreadSafe
public class Session extends chat.viska.xmpp.plugins.jingle.Session {

  private final PeerConnectionFactory factory;
  private final PeerConnection connection;
  private final Set<Content> contents = new HashSet<>();
  private final StandardProperty<State> state = new StandardProperty<>(State.CREATED);

  private final PeerConnection.Observer connectionObserver = new PeerConnection.Observer() {

    @Override
    public void onSignalingChange(final PeerConnection.SignalingState state) {
      synchronized (Session.this) {
        final boolean negotiated = Session.this.state.get() == State.NEGOTIATED;
        if (state == PeerConnection.SignalingState.STABLE && negotiated) {
          Session.this.state.change(State.ACTIVE);
        } if (state == PeerConnection.SignalingState.CLOSED) {
          Session.this.state.change(State.TERMINATED);
        }
      }

    }

    @Override
    public void onIceConnectionChange(final PeerConnection.IceConnectionState state) { }

    @Override
    public void onIceConnectionReceivingChange(final boolean recieving) {}

    @Override
    public void onIceGatheringChange(final PeerConnection.IceGatheringState state) {}

    @Override
    public void onIceCandidate(final IceCandidate candidate) { }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {}

    @Override
    public void onAddStream(final MediaStream mediaStream) {}

    @Override
    public void onRemoveStream(final MediaStream mediaStream) {}

    @Override
    public void onDataChannel(final DataChannel dataChannel) {}

    @Override
    public void onRenegotiationNeeded() {}

    @Override
    public void onAddTrack(final RtpReceiver receiver, final MediaStream[] streams) {}
  };

  public class AudioContent extends RtpContent {

    private AudioContent(final MediaStreamTrack track) {
      enabledProperty().getStream().subscribe(it -> {
        synchronized (Session.this) {
          track.setEnabled(it);
        }
      });
    }

    @Override
    public MediaType getMediaType() {
      return MediaType.AUDIO;
    }
  }

  /**
   * Default constructor.
   */
  public Session(final String id,
                 final Jid peer,
                 final PeerConnectionFactory factory,
                 final List<PeerConnection.IceServer> iceServers) {
    super(id, peer);
    this.factory = factory;
    connection = factory.createPeerConnection(iceServers, connectionObserver);
    state
        .getStream()
        .filter(it -> it == State.TERMINATED)
        .observeOn(Schedulers.io())
        .subscribe(it -> connection.dispose());
  }

  /**
   * Adds a local {@link AudioTrack}.
   * @param lsGroups Lip syncing {@link ContentGroup}s that the {@link AudioTrack} will be added to.
   */
  public synchronized Content addAudioTrack(final Set<String> lsGroups) {
    if (state.get() == State.CREATED) {
      state.change(State.PREPARING_OFFER);
    } else if (state.get() != State.PREPARING_ANSWER && state.get() != State.PREPARING_OFFER) {
      throw new IllegalStateException();
    }

    final AudioTrack track = factory.createAudioTrack(
        UUID.randomUUID().toString(),
        factory.createAudioSource(new MediaConstraints())
    );
    connection.addTrack(track, new ArrayList<>(lsGroups));
    final Content content = new AudioContent(track);
    contents.add(content);
    return content;
  }

  @Override
  public synchronized Description createOffer() {
    if (state.get() != State.PREPARING_OFFER) {
      throw new IllegalStateException();
    }
    final SingleSubject<SessionDescription> sdp = SingleSubject.create();
    connection.createOffer(new SdpCreationObserver(sdp), new MediaConstraints());
    final Description description = SdpParser.parse(sdp.blockingGet().description);

    state.change(State.OFFER_SENT);
    return description;
  }

  @Override
  public synchronized Description createAnswer() {
    if (state.get() != State.PREPARING_ANSWER) {
      throw new IllegalStateException();
    }
    final SingleSubject<SessionDescription> sdp = SingleSubject.create();
    connection.createAnswer(new SdpCreationObserver(sdp), new MediaConstraints());
    final Description description = SdpParser.parse(sdp.blockingGet().description);

    state.change(State.NEGOTIATED);
    return description;
  }

  @Override
  public synchronized void applyRemoteDescription(Description description) {
    if (state.get() != State.CREATED && state.get() != State.OFFER_SENT) {
      throw new IllegalStateException();
    }

    final CompletableSubject result = CompletableSubject.create();
    connection.setRemoteDescription(
        new SdpSetObserver(result),
        new SessionDescription(SessionDescription.Type.ANSWER, SdpParser.parse(description))
    );

    if (state.get() == State.CREATED) {
      state.change(State.PREPARING_ANSWER);
    } else {
      state.change(State.NEGOTIATED);
    }
  }

  @Override
  public Set<Content> getLocalContents() {
    return Collections.unmodifiableSet(contents);
  }

  @Override
  public Property<State> stateProperty() {
    return state;
  }

  @Override
  public synchronized void terminate() {
    connection.close();
  }
}