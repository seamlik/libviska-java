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

import io.reactivex.Maybe;

/**
 * Receipt of sending an {@code <iq/>}.
 */
public class IqReceipt extends StanzaReceipt {

  private final Maybe<Stanza> response;

  /**
   * Default constructor.
   */
  public IqReceipt(Maybe<?> serverAck, Maybe<Stanza> response) {
    super(serverAck);
    this.response = response;
  }

  /**
   * Gets a token of response to the sent {@code <iq/>}. For an {@code <iq/>} of
   * {@link chat.viska.xmpp.Stanza.IqType#RESULT} or {@link chat.viska.xmpp.Stanza.IqType#ERROR} it
   * signals a completion immediately. Signals a completion if no result is ever received before the
   * {@link Session} is disposed of. Signals an error if a stanza error is received.
   */
  public Maybe<Stanza> getResponse() {
    return response;
  }
}