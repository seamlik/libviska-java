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
import rxbeans.Property;
import rxbeans.StandardProperty;

/**
 * <a href="https://xmpp.org/extensions/xep-0166.html">Jingle</a> session.
 */
public abstract class Session {

  private final StandardProperty<Boolean> active = new StandardProperty<>(false);
  private String id;
  private Jid initiator;
  private Jid responder;

  public Session(final String id, final Jid initiator, final Jid responder) {
    this.id = id;
    this.initiator = initiator;
    this.responder = responder;
  }

  public String getId() {
    return id;
  }

  public Jid getInitiator() {
    return initiator;
  }

  public Jid getResponder() {
    return responder;
  }

  public Property<Boolean> activeProperty() {
    return active;
  }
}