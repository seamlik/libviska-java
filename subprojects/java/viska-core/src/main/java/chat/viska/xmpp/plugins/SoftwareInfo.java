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

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Information of an XMPP software. This class is part of
 * <a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software
 * Version</a>.
 */
public class SoftwareInfo {

  private String name;
  private String version;
  private String operatingSystem;

  /**
   * Default constructor.
   */
  public SoftwareInfo(final String name, final String version, final String operatingSystem) {
    this.name = name;
    this.version = version;
    this.operatingSystem = operatingSystem;
  }

  /**
   * Gets the software name.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the software version.
   */
  public String getVersion() {
    return version;
  }

  /**
   * Gets the operating system name.
   */
  public String getOperatingSystem() {
    return operatingSystem;
  }

  @Override
  public boolean equals(@Nullable final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    SoftwareInfo that = (SoftwareInfo) obj;
    return Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(operatingSystem, that.operatingSystem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, version, operatingSystem);
  }
}