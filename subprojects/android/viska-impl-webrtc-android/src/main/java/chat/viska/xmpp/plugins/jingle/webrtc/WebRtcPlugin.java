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

import chat.viska.xmpp.CommonXmlns;
import chat.viska.commons.XmlTagSignature;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.plugins.jingle.Content;
import chat.viska.xmpp.plugins.jingle.JinglePlugin;
import chat.viska.xmpp.plugins.jingle.SessionPlugin;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.concurrent.ThreadSafe;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;

/**
 * Provides {@link chat.viska.xmpp.plugins.jingle.Session}s using WebRTC.
 *
 * <p>Because of the differences between the protocols of WebRTC and Jingle, this plugin does not
 * support content modifications to an active {@link chat.viska.xmpp.plugins.jingle.Session}. In
 * other words, this plugin unconditionally rejects any Jingle actions of the following:</p>
 *
 * <ul>
 *   <li>{@code content-add}</li>
 *   <li>{@code content-remove}</li>
 * </ul>
 *
 * <p>Any methods of a {@link Session} that modify local {@link Content} while it is active will
 * also throw a {@link IllegalStateException}.</p>
 *
 * <p>This is mainly because Jingle modifies a session by sending incremental data while WebRTC
 * sends a session description that covers the new session after the modifications.</p>
 *
 * <p>Additionally, this plugin rejects any {@code transport-replace} unconditionally because WebRTC
 * only supports <a href="https://datatracker.ietf.org/doc/rfc5245">ICE</a>.</p>
 */
@Plugin.Features({
    CommonXmlns.JINGLE_ICE,
    CommonXmlns.JINGLE_RTP_AUDIO
})
@ThreadSafe
public class WebRtcPlugin implements SessionPlugin {

  private final List<PeerConnection.IceServer> iceServers = new CopyOnWriteArrayList<>();
  private @MonotonicNonNull PeerConnectionFactory factory;
  private @MonotonicNonNull JinglePlugin jinglePlugin;

  private PeerConnectionFactory getFactory() {
    if (factory == null) {
      throw new IllegalStateException();
    }
    return factory;
  }

  private JinglePlugin getJinglePlugin() {
    if (jinglePlugin == null) {
      throw new IllegalStateException();
    }
    return jinglePlugin;
  }

  /**
   * Sets the factory for WebRTC objects.
   *
   * <p>This method must be called at least once before the plugin can be used.</p>
   */
  public void setPeerConnectionFactory(final PeerConnectionFactory factory) {
    this.factory = factory;
  }

  public List<PeerConnection.IceServer> getIceServers() {
    return iceServers;
  }

  @Override
  public Session createSession(final String id, final Jid peer) {
    return new Session(id, peer, getFactory(), iceServers);
  }

  @Override
  public void onApply(final chat.viska.xmpp.Session.PluginContext context) {}
}