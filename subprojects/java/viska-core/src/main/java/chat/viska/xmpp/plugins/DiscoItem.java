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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Result of an {@code #items} query defined in
 * <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>.
 */
public class DiscoItem {

  private final Jid jid;
  private final String name;
  private final String node;

  public DiscoItem(@Nullable final Jid jid,
                   @Nonnull final String name,
                   @Nonnull final String node) {
    this.jid = jid;
    this.name = name == null ? "" : name;
    this.node = node == null ? "" : node;
  }

  /**
   * Gets the JID.
   */
  @Nonnull
  public Jid getJid() {
    return jid;
  }

  /**
   * Gets the name.
   */
  @Nonnull
  public String getName() {
    return name;
  }

  @Nonnull
  public String getNode() {
    return node;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final DiscoItem item = (DiscoItem) obj;
    return Objects.equals(jid, item.jid)
        && Objects.equals(name, item.name)
        && Objects.equals(node, item.node);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jid, name, node);
  }
}