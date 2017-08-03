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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;

public class LocalClient extends AbstractClient {

  private AtomicReference<String> softwareName = new AtomicReference<>("");
  private AtomicReference<String> softwareVersion = new AtomicReference<>("");
  private AtomicReference<String> operatingSystem = new AtomicReference<>(
      String.format(
          "%1s %2s",
          System.getProperty("os.name", "Unknown"),
          System.getProperty("os.version", "")
      ).trim()
  );
  private AtomicReference<DiscoClientType> discoClientType = new AtomicReference<>();

  LocalClient(final @NonNull Session session) {
    super(session, session.getJid());
  }

  public void setSoftwareName(@Nullable final String softwareName) {
    this.softwareName.set(softwareName == null ? "" : softwareName);
  }

  public void setSoftwareVersion(@Nullable final String softwareVersion) {
    this.softwareVersion.set(softwareVersion == null ? "" : softwareVersion);
  }

  public void setOperatingSystem(@Nullable final String operatingSystemName) {
    this.operatingSystem.set(operatingSystemName == null ? "" : operatingSystemName);
  }

  public void setDiscoClientType(@Nullable final DiscoClientType type) {
    Objects.requireNonNull(type);
    discoClientType.set(type);
  }

  /**
   * Gets a {@link Set} of features enabled by all plugins and the session. This
   * method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return Empty {@link Set} if no feature is enabled.
   */
  @NonNull
  public Set<String> getDiscoFeatures() {
    final Set<String> features = new HashSet<>(getSession().getDiscoFeatures());
    for (Plugin plugin : getSession().getPluginManager().getPlugins()) {
      features.addAll(plugin.getDiscoFeatures());
    }
    return features;
  }
}