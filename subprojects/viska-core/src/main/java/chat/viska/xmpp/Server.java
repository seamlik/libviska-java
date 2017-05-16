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

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import java.beans.PropertyChangeEvent;
import java.util.AbstractMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.Validate;

public class Server extends AbstractEntity {

  private static final Map<Map.Entry<AbstractSession, String>, Server> SERVER_POOL = new ConcurrentHashMap<>();

  private String domain;

  public static Server getInstance(final @NonNull AbstractSession session,
                                   final @NonNull String domain) {
    if (session.getState() != AbstractSession.State.ONLINE) {
      throw new IllegalStateException();
    }
    final Map.Entry<AbstractSession, String> key = new AbstractMap.SimpleImmutableEntry<>(
        session,
        domain
    );
    Server server = SERVER_POOL.get(key);
    if (server != null) {
      return server;
    }
    server = new Server(session, domain);
    if (session.getState() == AbstractSession.State.DISPOSED) {
      return server;
    }
    session.getEventStream()
        .ofType(PropertyChangeEvent.class)
        .filter(new Predicate<PropertyChangeEvent>() {
          @Override
          public boolean test(final PropertyChangeEvent event) throws Exception {
            return event.getPropertyName().equals("State")
                && event.getNewValue() == AbstractSession.State.DISPOSED;
          }
        })
        .firstElement()
        .subscribe(new Consumer<PropertyChangeEvent>() {
          @Override
          public void accept(final PropertyChangeEvent event) throws Exception {
            Observable.fromIterable(SERVER_POOL.keySet())
                .filter(new Predicate<Map.Entry<AbstractSession, String>>() {
                  @Override
                  public boolean test(final Map.Entry<AbstractSession, String> entry)
                      throws Exception {
                    return entry.getKey() == session;
                  }
                })
                .subscribe(new Consumer<Map.Entry<AbstractSession, String>>() {
                  @Override
                  public void accept(final Map.Entry<AbstractSession, String> entry)
                      throws Exception {
                    SERVER_POOL.remove(entry);
                  }
                });
          }
        });
    return SERVER_POOL.put(key, server);
  }

  public Server(final @NonNull AbstractSession session,
                final @NonNull String domain) {
    super(session);
    Validate.notBlank(domain);
    this.domain = domain;
  }

  @Override
  public Jid getJid() {
    return new Jid(domain);
  }
}