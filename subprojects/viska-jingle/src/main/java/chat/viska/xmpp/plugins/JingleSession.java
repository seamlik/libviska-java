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

package chat.viska.xmpp.plugins;

import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.commons.reactive.ReactiveObject;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.SessionAware;
import javax.annotation.Nonnull;

/**
 * Jingle session.
 */
public class JingleSession implements SessionAware {

  public enum State {
    ACTIVE,
    ENDED,
    PENDING
  }

  private final Session session;
  private final String sessionId;
  private final MutableReactiveObject<State> state = new MutableReactiveObject<>();

  public JingleSession(@Nonnull final Session session,
                       @Nonnull final String sessionId) {
    this.session = session;
    this.sessionId = sessionId;
  }

  @Nonnull
  public ReactiveObject<State> getState() {
    return state;
  }

  @Nonnull
  public String getSessionId() {
    return sessionId;
  }

  @Override
  public Session getSession() {
    return session;
  }
}