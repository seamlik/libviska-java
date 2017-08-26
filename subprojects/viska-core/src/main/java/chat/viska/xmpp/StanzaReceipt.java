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
import io.reactivex.annotations.NonNull;

public class StanzaReceipt implements SessionAware{

  private final Session session;
  private final Maybe<Boolean> serverAck;
  private final Maybe<Stanza> response;

  public StanzaReceipt(@NonNull final Session session,
                       @NonNull final Maybe<Boolean> serverAck,
                       @NonNull final Maybe<Stanza> response) {
    this.session = session;
    this.serverAck = serverAck;
    this.response = response;
  }

  public Maybe<Boolean> getServerAcknowledment() {
    return serverAck;
  }

  public Maybe<Stanza> getResponse() {
    return response;
  }

  @Override
  public Session getSession() {
    return session;
  }
}