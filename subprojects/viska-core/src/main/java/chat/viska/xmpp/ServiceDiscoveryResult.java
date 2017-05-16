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
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class ServiceDiscoveryResult {

  public static class Identity {

  }

  private final Set<Identity> identities;
  private final Set<String> features;

  public ServiceDiscoveryResult(final @Nullable Collection<Identity> identities,
                                final @Nullable Collection<String> features) {
    this.identities = new HashSet<>(identities);
    this.features = new HashSet<>(features);
  }

  @NonNull
  public Set<Identity> getIdentities() {
    return new HashSet<>(identities);
  }

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
    ServiceDiscoveryResult that = (ServiceDiscoveryResult) obj;
    return Objects.equals(identities, that.identities) &&
        Objects.equals(features, that.features);
  }

  @Override
  public int hashCode() {
    return Objects.hash(identities, features);
  }
}