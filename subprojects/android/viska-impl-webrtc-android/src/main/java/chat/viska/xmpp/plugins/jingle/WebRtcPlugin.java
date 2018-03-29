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

package chat.viska.xmpp.plugins.jingle;

import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.webrtc.PeerConnectionFactory;

/**
 * {@link Plugin} supporting {@link RtpSession} using WebRTC.
 */
@Plugin.Features({
    CommonXmlns.JINGLE_ICE,
    CommonXmlns.JINGLE_RTP + "audio",
    CommonXmlns.JINGLE_RTP + "video"
})
@Plugin.DependsOn(JinglePlugin.class)
public class WebRtcPlugin implements RtpPlugin {

  private @MonotonicNonNull PeerConnectionFactory factory;

  public void setPeerConnectionFactory(PeerConnectionFactory factory) {
    this.factory = factory;
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    throw new UnsupportedOperationException();
  }

  @Override
  public RtpSession createRtpSession(final String id,
                                     Jid initiator,
                                     Jid responder,
                                     boolean audio,
                                     boolean video) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void onApplying(Session.PluginContext context) {}
}