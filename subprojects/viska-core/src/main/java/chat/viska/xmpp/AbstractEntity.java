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
import java.util.concurrent.Future;

public abstract class AbstractEntity implements SessionAware {

  private AbstractSession session;

  protected AbstractEntity(@NonNull AbstractSession session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  @NonNull
  public abstract Jid getJid();

  @NonNull
  public Future<ServiceDiscoveryResult> queryServices(AbstractEntity entity) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NonNull
  public AbstractSession getSession() {
    return session;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AbstractEntity that = (AbstractEntity) obj;
    return Objects.equals(session, that.session)
        && Objects.equals(getJid(), that.getJid());
  }

  @Override
  public int hashCode() {
    return Objects.hash(session, getJid());
  }
}