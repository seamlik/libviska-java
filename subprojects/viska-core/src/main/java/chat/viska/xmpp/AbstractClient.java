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

public abstract class AbstractClient extends AbstractEntity {

  private final Account account;

  protected AbstractClient(final @NonNull AbstractSession session,
                           final @NonNull Account account) {
    super(session);
    Objects.requireNonNull(account);
    this.account = account;
  }

  @NonNull
  public abstract String getResource();

  @NonNull
  public abstract Future<String[]> querySoftwareVersion();

  @NonNull
  public Account getAccount() {
    return account;
  }

  @Override
  @NonNull
  public Jid getJid() {
    return new Jid(
        account.getJid().getLocalPart(),
        account.getJid().getDomainPart(),
        getResource()
    );
  }
}