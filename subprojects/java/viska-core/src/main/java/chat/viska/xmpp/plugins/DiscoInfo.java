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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of an {@code #info} query defined in
 * <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>.
 */
public class DiscoInfo {

  /**
   * Identity of an XMPP entity.
   */
  public static class Identity {

    private final String category;
    private final String type;
    private final String name;

    public Identity(@Nullable final String category,
                    @Nullable final String type,
                    @Nullable final String name) {
      this.category = category == null ? "" : category;
      this.type = type == null ? "" : type;
      this.name = name == null ? "" : name;
    }

    /**
     * Gets the category.
     */
    @Nonnull
    public String getCategory() {
      return category;
    }

    /**
     * Gets the type.
     */
    @Nonnull
    public String getType() {
      return type;
    }

    /**
     * Gets the name.
     */
    @Nonnull
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
      return Objects.equals(category, identity.category)
          && Objects.equals(type, identity.type)
          && Objects.equals(name, identity.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(category, type, name);
    }

    @Override
    @Nonnull
    public String toString() {
      return String.format(
          "Category: %1s, Type: %2s, Name: %3s",
          category,
          type,
          name
      );
    }
  }

  private final List<Identity> identities;
  private final List<String> features;

  /**
   * Default constructor.
   */
  public DiscoInfo(@Nullable final Collection<Identity> identities,
                   @Nullable final Collection<String> features) {
    this.identities = identities == null
        ? Collections.emptyList()
        : new ArrayList<>(identities);
    this.features = features == null
        ? Collections.emptyList()
        : new ArrayList<>(features);
  }

  /**
   * Gets the identities.
   * @return Unmodifiable set.
   */
  @Nonnull
  public List<Identity> getIdentities() {
    return Collections.unmodifiableList(identities);
  }

  /**
   * Gets the features.
   * @return Unmodifiable set.
   */
  @Nonnull
  public List<String> getFeatures() {
    return Collections.unmodifiableList(features);
  }

  @Override
  public boolean equals(final Object obj) {
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