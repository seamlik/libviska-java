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

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Objects;

/**
 * Item in a Service Discovery query result. This class is part of
 * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
 * Discovery</a>.
 */
public class DiscoItem {

  private Jid jid;
  private String name;

  public DiscoItem(final @Nullable Jid jid, final @NonNull String name) {
    this.jid = jid;
    this.name = name == null ? "" : name;
  }

  /**
   * Gets the Jabber/XMPP ID.
   */
  @NonNull
  public Jid getJid() {
    return jid;
  }

  /**
   * Gets the name.
   */
  @NonNull
  public String getName() {
    return name;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    DiscoItem discoItem = (DiscoItem) obj;
    return Objects.equals(jid, discoItem.jid)
        && Objects.equals(name, discoItem.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(jid, name);
  }
}