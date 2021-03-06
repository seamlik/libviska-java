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

package chat.viska.xmpp.plugins.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Result of an {@code #info} query defined in
 * <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>.
 */
public class DiscoInfo {

  /**
   * Identity of an XMPP entity.
   */
  public static class Identity {

    public final String category;
    public final String type;
    public final String name;

    public Identity(final String category,
                    final String type,
                    final String name) {
      this.category = category;
      this.type = type;
      this.name = name;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Identity identity = (Identity) obj;
      return Objects.equals(category, identity.category)
          && Objects.equals(type, identity.type)
          && Objects.equals(name, identity.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(category, type, name);
    }

    @Override
    public String toString() {
      return String.format(
          "Category: %1s, Type: %2s, Name: %3s",
          category,
          type,
          name
      );
    }
  }

  public final List<Identity> identities;
  public final List<String> features;

  /**
   * Default constructor.
   */
  public DiscoInfo(final Collection<Identity> identities,
                   final Collection<String> features) {
    this.identities = Collections.unmodifiableList(new ArrayList<>(identities));
    this.features = Collections.unmodifiableList(new ArrayList<>(features));
  }

  @Override
  public boolean equals(@Nullable final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final DiscoInfo that = (DiscoInfo) obj;
    return Objects.equals(identities, that.identities)
        && Objects.equals(features, that.features);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identities, features);
  }
}