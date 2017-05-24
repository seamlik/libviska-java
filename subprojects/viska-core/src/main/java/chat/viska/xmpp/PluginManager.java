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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Contains information of {@link Plugin}s applied on an {@link DefaultSession}.
 */
public class PluginManager implements SessionAware {

  private final DefaultSession session;
  private final List<Plugin> appliedPlugins = new ArrayList<>();

  PluginManager(final @NonNull DefaultSession session) {
    this.session = session;
  }

  /**
   * Applies a {@link Plugin}. This method does nothing if the plugin
   * has already been applied.
   */
  public void apply(final @NonNull Class<? extends Plugin> type) {
    Objects.requireNonNull(type);
    if (getPlugin(type) != null) {
      return;
    }
    final Plugin plugin;
    try {
      plugin = type.getConstructor(DefaultSession.class).newInstance(session);
    } catch (Exception ex) {
      throw new PluginUnappliableException(ex);
    }
    appliedPlugins.add(plugin);
  }

  /**
   * Gets an applied plugin which is of a particular type.
   * @return {@code null} if the plugin cannot be found.
   */
  @Nullable
  public Plugin getPlugin(Class<? extends Plugin> type) {
    for (Plugin plugin : appliedPlugins) {
      if (type.isInstance(plugin)) {
        return plugin;
      }
    }
    return null;
  }

  /**
   * Gets a {@link Set} of features enabled by all plugins and the session. This
   * method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return Empty {@link Set} if no feature is enabled.
   */
  @NonNull
  public Set<String> getAllFeatures() {
    final Set<String> features = new HashSet<>(session.getFeatures());
    for (Plugin plugin : appliedPlugins) {
      features.addAll(plugin.getFeatures());
    }
    return features;
  }

  @Override
  @NonNull
  public DefaultSession getSession() {
    return session;
  }
}