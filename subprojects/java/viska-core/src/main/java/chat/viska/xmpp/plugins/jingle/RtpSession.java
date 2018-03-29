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

import chat.viska.xmpp.Jid;

/**
 * <a href="https://xmpp.org/extensions/xep-0167.html">Jingle RTP session</a>.
 */
public abstract class RtpSession extends Session {

  /**
   * Description for an {@link RtpSession}.
   */
  public static class Description {

  }

  /**
   * Default constructor.
   */
  protected RtpSession(final String id, final Jid initiator, final Jid responder) {
    super(id, initiator, responder);
  }

  public abstract Description generateInitiationOffer();
}