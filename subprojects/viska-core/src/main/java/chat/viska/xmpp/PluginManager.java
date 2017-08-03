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
import io.reactivex.annotations.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Contains information of {@link Plugin}s applied on an {@link DefaultSession}.
 */
public class PluginManager implements SessionAware {

  private final Session session;
  private final Set<Plugin> plugins = new HashSet<>();

  PluginManager(final @NonNull DefaultSession session) {
    this.session = session;
    apply(BasePlugin.class);
  }

  /**
   * Applies a {@link Plugin}. This method does nothing if the plugin
   * has already been applied.
   */
  public void apply(final @NonNull Class<? extends Plugin> type)
      throws IllegalArgumentException {
    Objects.requireNonNull(type);
    if (getPlugin(type) != null) {
      return;
    }
    final Plugin plugin;
    try {
      plugin = type.getConstructor(Session.class).newInstance(session);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
    plugins.add(plugin);
  }

  /**
   * Gets an applied plugin which is of a particular type.
   * @return {@code null} if the plugin cannot be found.
   */
  @Nullable
  public Plugin getPlugin(Class<? extends Plugin> type) {
    for (Plugin plugin : plugins) {
      if (type.isInstance(plugin)) {
        return plugin;
      }
    }
    return null;
  }

  @NonNull
  public Set<Plugin> getPlugins() {
    return Collections.unmodifiableSet(plugins);
  }

  @Override
  @NonNull
  public Session getSession() {
    return session;
  }
}