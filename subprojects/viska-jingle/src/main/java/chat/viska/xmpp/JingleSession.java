/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.xmpp;

import chat.viska.xmpp.SessionAware;
import java.util.UUID;

/**
 * Jingle session.
 * @since 0.1
 */
public class JingleSession implements SessionAware {

  public enum State {
    ACTIVE,
    ENDED,
    PENDING
  }

  private UUID innerId = UUID.randomUUID();
  private String sessionId;

  private State state;

  public State getState() {
    return state;
  }

  public chat.viska.xmpp.Session getSession() {
    throw new RuntimeException();
  }
}