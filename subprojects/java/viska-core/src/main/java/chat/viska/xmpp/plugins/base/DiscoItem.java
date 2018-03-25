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

package chat.viska.xmpp.plugins.base;

import chat.viska.xmpp.Jid;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Result of an {@code #items} query defined in
 * <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>.
 */
public class DiscoItem {

  private final Jid jid;
  private final String name;
  private final String node;

  public DiscoItem(final Jid jid,
                   final String name,
                   final String node) {
    this.jid = jid;
    this.name = name;
    this.node = node;
  }

  /**
   * Gets the JID.
   */
  public Jid getJid() {
    return jid;
  }

  /**
   * Gets the name.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the node.
   */
  public String getNode() {
    return node;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
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