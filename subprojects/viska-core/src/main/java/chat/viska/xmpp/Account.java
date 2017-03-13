/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package chat.viska.xmpp;

import java.util.Set;

/**
 * @since 0.1
 */
public abstract class Account implements SessionAware {
  private Session session;
  private Jid jid;

  public abstract Set<String> getFeatures();

  protected Account(Jid jid, Session session) {
    this.jid = jid.toBareJid();
    this.session = session;
  }

  @Override
  public Session getSession() {
    return session;
  }

  public Jid getJid() {
    return jid;
  }
}