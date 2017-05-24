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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Result of a service discovery query. This class is part of
 * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
 * Discovery</a>.
 */
public class DiscoInfo {

  /**
   * Identity of an {@link AbstractEntity}.
   */
  public static class Identity {

    private final String category;
    private final String type;
    private final String name;

    public Identity(final @Nullable String category,
                    final @Nullable String type,
                    final @Nullable String name) {
      this.category = category == null ? "" : category;
      this.type = type == null ? "" : type;
      this.name = name == null ? "" : name;
    }

    /**
     * Gets the category.
     */
    @NonNull
    public String getCategory() {
      return category;
    }

    /**
     * Gets the type.
     */
    @NonNull
    public String getType() {
      return type;
    }

    /**
     * Gets the name.
     */
    @NonNull
    public String getName() {
      return name;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      Identity identity = (Identity) obj;
      return Objects.equals(category, identity.category) &&
          Objects.equals(type, identity.type) &&
          Objects.equals(name, identity.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(category, type, name);
    }
  }

  private final Set<Identity> identities;
  private final Set<String> features;

  public DiscoInfo(final @Nullable Collection<Identity> identities,
                   final @Nullable Collection<String> features) {
    if (identities == null) {
      this.identities = new HashSet<>(0);
    } else {
      this.identities = new HashSet<>(identities);
    }
    if (features == null) {
      this.features = new HashSet<>(0);
    } else {
      this.features = new HashSet<>(features);
    }
  }

  public DiscoInfo(final @Nullable Identity[] identities,
                   final @Nullable String[] features) {
    if (identities == null) {
      this.identities = new HashSet<>(0);
    } else {
      this.identities = new HashSet<>(Arrays.asList(identities));
    }
    if (features == null) {
      this.features = new HashSet<>(0);
    } else {
      this.features = new HashSet<>(Arrays.asList(features));
    }
  }

  /**
   * Gets the identities.
   */
  @NonNull
  public Set<Identity> getIdentities() {
    return new HashSet<>(identities);
  }

  /**
   * Gets the features.
   */
  @NonNull
  public Set<String> getFeatures() {
    return new HashSet<>(features);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    DiscoInfo that = (DiscoInfo) obj;
    return Objects.equals(identities, that.identities) &&
        Objects.equals(features, that.features);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identities, features);
  }
}