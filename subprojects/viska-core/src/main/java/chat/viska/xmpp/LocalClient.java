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

public class LocalClient extends AbstractClient {

  private String softwareName = "";
  private String softwareVersion = "";
  private String operatingSystem = String.format(
          "%1s %2s",
          System.getProperty("os.name", "Unknown"),
          System.getProperty("os.version", "")
  ).trim();
  private DiscoClientType discoClientType;

  LocalClient(@NonNull final Session session) {
    super(session, session.getJid());
  }

  @NonNull
  public String getSoftwareName() {
    return softwareName;
  }

  @NonNull
  public String getSoftwareVersion() {
    return softwareVersion;
  }

  @NonNull
  public String getOperatingSystem() {
    return operatingSystem;
  }

  @Nullable
  public DiscoClientType getDiscoClientType() {
    return discoClientType;
  }

  public void setSoftwareName(@Nullable final String softwareName) {
    this.softwareName = softwareName == null ? "" : softwareName;
  }

  public void setSoftwareVersion(@Nullable final String softwareVersion) {
    this.softwareVersion = softwareVersion == null ? "" : softwareVersion;
  }

  public void setOperatingSystem(@Nullable final String operatingSystemName) {
    this.operatingSystem = operatingSystemName == null ? "" : operatingSystemName;
  }

  public void setDiscoClientType(@Nullable final DiscoClientType type) {
    Objects.requireNonNull(type);
    discoClientType = type;
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
    final Set<String> features = new HashSet<>();
    for (Plugin plugin : getSession().getPluginManager().getPlugins()) {
      features.addAll(plugin.getDiscoFeatures());
    }
    return features;
  }
}