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
import java.util.Objects;
import org.apache.commons.lang3.Validate;

/**
 * Version of the XMPP standard.
 */
public class Version implements Comparable<Version> {

  private int major;
  private int minor;

  public Version(int major, int minor) {
    Validate.isTrue(major >= 0);
    Validate.isTrue(minor >= 0);
    this.major = major;
    this.minor = minor;
  }

  public Version(String version) {
    String[] parts = version.split("\\.");
    major = Integer.parseInt(parts[0]);
    minor = Integer.parseInt(parts[1]);
    Validate.isTrue(major >= 0);
    Validate.isTrue(minor >= 0);
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  @Override
  public int compareTo(Version version) {
    final int majorDiff = Integer.compare(major, version.major);
    if (majorDiff != 0) {
      return majorDiff;
    }
    return Integer.compare(minor, version.minor);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final Version version = (Version) obj;
    return major == version.major && minor == version.minor;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor);
  }

  @Override
  @NonNull
  public String toString() {
    return major + "." + minor;
  }
}