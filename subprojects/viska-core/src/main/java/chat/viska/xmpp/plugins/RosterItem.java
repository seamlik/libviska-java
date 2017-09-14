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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class RosterItem {

  public enum Subscription {
    BOTH,
    FROM,
    NONE,
    TO
  }

  private final Jid jid;
  private final Set<String> groups = new HashSet<>();
  private final Subscription subscription;
  private final String name;

  public RosterItem(@NonNull final Jid jid,
                    @Nullable final Subscription subscription,
                    @Nullable final String name,
                    @Nullable final Collection<String> groups) {
    Objects.requireNonNull(jid);
    this.jid = jid;
    this.subscription = subscription;
    this.name = name == null ? "" : name;
    if (groups != null) {
      this.groups.addAll(groups);
    }
  }

  public Jid getJid() {
    return jid;
  }

  public Set<String> getGroups() {
    return Collections.unmodifiableSet(groups);
  }

  public String getName() {
    return name;
  }

  public Subscription getSubscription() {
    return subscription;
  }


}