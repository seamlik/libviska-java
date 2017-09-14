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

import chat.viska.xmpp.plugins.BasePlugin;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.exceptions.OnErrorNotImplementedException;
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

  PluginManager(@NonNull final Session session) {
    this.session = session;
    getSession().getState().getStream().filter(
        it -> it == Session.State.ONLINE
    ).forEach(
        it -> Observable.fromIterable(getPlugins()).forEach(Plugin::onSessionOnline)
    );
    getSession().getState().getStream().filter(
        it -> it == Session.State.DISCONNECTED
    ).forEach(
        it -> Observable.fromIterable(getPlugins()).forEach(Plugin::onSessionDisconnected)
    );
    getSession().getState().getStream().filter(
        it -> it == Session.State.DISPOSED
    ).forEach(
        it -> Observable.fromIterable(getPlugins()).forEach(Plugin::onSessionDisposed)
    );
    apply(BasePlugin.class);
  }

  /**
   * Applies a {@link Plugin}. This method does nothing if the plugin
   * has already been applied.
   * @throws IllegalArgumentException If it fails to apply the {@link Plugin}.
   */
  public void apply(final @NonNull Class<? extends Plugin> type)
      throws IllegalArgumentException {
    Objects.requireNonNull(type);
    if (getPlugin(type) != null) {
      return;
    }
    final Plugin plugin;
    try {
      plugin = type.getConstructor().newInstance();
    } catch (Exception ex) {
      throw new IllegalArgumentException(
          "Unable to instantiate plugin " + type.getCanonicalName(),
          ex
      );
    }
    try {
      Observable.fromIterable(plugin.getDependencies()).forEach(this::apply);
    } catch (OnErrorNotImplementedException ex) {
      throw new IllegalArgumentException(
          "Unable to apply dependencies of plugin " + type.getCanonicalName(),
          ex.getCause()
      );
    }
    this.plugins.add(plugin);
    plugin.onApplied(getSession());
    if (getSession().getState().getValue() == Session.State.ONLINE) {
      plugin.onSessionOnline();
    }
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
  public Session getSession() {
    return session;
  }
}