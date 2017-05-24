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

import io.reactivex.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.Future;

/**
 * XMPP user account.
 */
public class Account extends AbstractEntity {

  private Jid bareJid;
  private Server server;

  protected Account(@NonNull Session session,
                    @NonNull Jid bareJid) {
    super(session);
    Objects.requireNonNull(bareJid);
    this.bareJid = bareJid.toBareJid();
    final BasePlugin plugin = (BasePlugin)
        session.getPluginManager().getPlugin(BasePlugin.class);
    server = (Server) plugin.getXmppEntityInstance(this.bareJid);
  }

  /**
   * Gets the nickname. This method is part of
   * <a href="https://xmpp.org/extensions/xep-0172.html">XEP-0172: User
   * Nickname</a>.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Future<String> getNickname() {
    throw new UnsupportedOperationException();
  }

  /**
   * Gets the home {@link Server}.
   */
  @NonNull
  public Server getServer() {
    return server;
  }

  @Override
  @NonNull
  public Jid getJid() {
    return bareJid;
  }
}