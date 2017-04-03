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

import java.util.HashSet;
import java.util.Set;

/**
 * Contains information about plugins applied on a {@link Session}.
 */
public final class PluginManager implements SessionAware {

  private Session session;
  private Set<Plugin> appliedPlugins = new HashSet<>();

  PluginManager(Session session) {
    this.session = session;
  }

  public void apply(Class<? extends Plugin> type) {
    Plugin plugin;
    try {
      plugin = type.getConstructor(Session.class).newInstance(session);
    } catch (Exception ex) {
      throw new PluginUnappliableException(ex);
    }
    appliedPlugins.add(plugin);
  }

  public boolean hasPlugin(Class<? extends Plugin> type) {
    for (Plugin plugin : appliedPlugins) {
      if (type.isInstance(plugin)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Session getSession() {
    return session;
  }
}