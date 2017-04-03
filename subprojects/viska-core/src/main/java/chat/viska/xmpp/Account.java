/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;
import java.util.Set;
import org.apache.commons.lang3.Validate;

/**
 * @since 0.1
 */
public abstract class Account implements SessionAware {

  private Session session;
  private Jid jid;

  public abstract @NonNull Set<String> getFeatures();

  protected Account(@NonNull Jid jid, @NonNull Session session) {
    Validate.notNull(jid, "`jid` must not be null.");
    Validate.notNull(session, "`session` must not be null.");
    this.jid = jid.toBareJid();
    this.session = session;
  }

  @Override
  public @NonNull Session getSession() {
    return session;
  }

  public @NonNull Jid getJid() {
    return jid;
  }
}