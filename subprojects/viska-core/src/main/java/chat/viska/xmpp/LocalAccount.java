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
 * An XMPP account logged into an XMPP session.
 * @since 0.1
 */
public class LocalAccount extends Account implements SessionAware {

  private Session session;

  public LocalAccount(@NonNull Jid jid, @NonNull Session session) {
    super(jid);
    Validate.notNull(session);
    this.session = session;
  }

  public void setPassword(String password) {

  }

  @Override
  public Set<String> getFeatures() {
    return null;
  }

  @Override
  public Set<String> getResources() {
    return null;
  }

  @Override
  public Session getSession() {
    return session;
  }
}