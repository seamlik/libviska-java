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

/**
 * XMPP entity.
 */
public abstract class AbstractEntity implements SessionAware {

  private Session session;

  protected AbstractEntity(final @NonNull Session session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  /**
   * Gets the Jabber/XMPP ID.
   */
  @NonNull
  public abstract Jid getJid();

  /**
   * Queries available features and {@link chat.viska.xmpp.DiscoInfo.Identity}s.
   * This method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a>.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Future<DiscoInfo> queryFeatures() {
    throw new UnsupportedOperationException();
  }

  /**
   * Queries other {@link AbstractEntity}s associated to this
   * {@link AbstractEntity}.
   * @return {@link Future} tracking the completion status of this method and
   *         providing a way to cancel it.
   */
  @NonNull
  public Future<DiscoItem> queryItems() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NonNull
  public Session getSession() {
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