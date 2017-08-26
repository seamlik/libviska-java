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

import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.SessionAware;
import io.reactivex.Maybe;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Set;

public class Roster implements SessionAware {

  public interface Item {

    enum Subscription {
      BOTH,
      FROM,
      NONE,
      TO
    }

    @NonNull
    Jid getJid();

    @Nullable
    Subscription getSubscription();

    @NonNull
    Set<String> getGroups();

    @NonNull
    String getName();
  }

  private final Session session;

  Roster(@NonNull final Session session) {
    this.session = session;
  }

  @NonNull
  public Maybe<Boolean> sync() {
    throw new UnsupportedOperationException();
  }

  @NonNull
  public Set<LiveRosterItem> get() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Session getSession() {
    return session;
  }
}