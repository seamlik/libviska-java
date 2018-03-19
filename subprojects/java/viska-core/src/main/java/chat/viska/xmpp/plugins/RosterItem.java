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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Roster item.
 */
public class RosterItem {

  public enum Subscription {
    BOTH,
    FROM,
    NONE,
    TO
  }

  private final Jid jid;
  private final Set<String> groups;
  private final @Nullable Subscription subscription;
  private final String name;

  /**
   * Default contructor.
   */
  public RosterItem(final Jid jid,
                    @Nullable final Subscription subscription,
                    final String name,
                    final Collection<String> groups) {
    this.jid = jid;
    this.subscription = subscription;
    this.name = name;
    this.groups = Collections.unmodifiableSet(new HashSet<>(groups));
  }

  /**
   * Gets the JID.
   */
  public Jid getJid() {
    return jid;
  }

  /**
   * Gets the group names.
   */
  public Set<String> getGroups() {
    return groups;
  }

  /**
   * Gets the contact name.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the subscription.
   */
  @Nullable
  public Subscription getSubscription() {
    return subscription;
  }
}