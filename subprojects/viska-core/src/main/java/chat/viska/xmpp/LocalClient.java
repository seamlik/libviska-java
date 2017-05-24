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
import java.util.concurrent.Future;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;

public class LocalClient extends AbstractClient {

  private String softwareName = "";
  private String softwareVersion = "";
  private String operatingSystem = String.format(
      "%1s %2s",
      System.getProperty("os.name", "Unknown"),
      System.getProperty("os.version", "")
  ).trim();

  public LocalClient(final @NonNull Session session) {
    super(
        session,
        (Account) ((BasePlugin) session.getPluginManager().getPlugin(BasePlugin.class)).getXmppEntityInstance(new Jid(session.getUsername(), session.getConnection().getDomain(), null))
    );
  }

  public Future<Void> setResource() {
    throw new UnsupportedOperationException();
  }

  public void setSoftwareName(String softwareName) {
    this.softwareName = softwareName == null ? "" : softwareName;
  }

  public void setSoftwareVersion(String softwareVersion) {
    this.softwareVersion = softwareVersion == null ? "" : softwareVersion;
  }

  public void setOperatingSystem(String operatingSystemName) {
    this.operatingSystem = operatingSystemName == null ? "" : operatingSystemName;
  }

  @Override
  @NonNull
  public Future<SoftwareInfo> querySoftwareVersion() {
    return ConcurrentUtils.constantFuture(
        new SoftwareInfo(softwareName, softwareVersion, operatingSystem)
    );
  }

  @Override
  @NonNull
  public String getResource() {
    return getSession().getResource();
  }
}