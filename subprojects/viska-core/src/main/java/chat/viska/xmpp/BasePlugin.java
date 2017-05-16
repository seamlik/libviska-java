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
import io.reactivex.annotations.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BasePlugin extends AbstractPlugin {

  public static final String XMLNS_SOFTWARE_VERSION = "jabber:iq:version";
  public static final String XMLNS_SERVICE_DISCOVERY = "http://jabber.org/protocol/disco";

  private static final String[] fixedFeatures = new String[] {
      XMLNS_SOFTWARE_VERSION
  };

  private final Map<Jid, AbstractEntity> xmppEntityPool = new ConcurrentHashMap<>();
  private LocalClient client;

  public BasePlugin(final @NonNull AbstractSession session) {
    super(session);
  }

  @Nullable
  public AbstractEntity getXmppEntityInstance(final @NonNull Jid jid) {
    Objects.requireNonNull(jid);
    if (getSession().getState() == AbstractSession.State.DISPOSED) {
      throw new IllegalStateException();
    }
    AbstractEntity entity = xmppEntityPool.get(jid);
    if (entity != null) {
      return entity;
    }
    if (jid.getLocalPart().isEmpty()) {
      return xmppEntityPool.put(
          jid,
          new Server(getSession(), jid.getDomainPart())
      );
    } else if (jid.getResourcePart().isEmpty()) {
      return xmppEntityPool.put(
          jid,
          new Account(getSession(), jid)
      );
    } else if (jid.getLocalPart().equals(getSession().getUsername())
            && jid.getDomainPart().equals(getSession().getConnection().getDomain())
            && jid.getResourcePart().equals(getSession().getResource())) {
      return getLocalClient();
    } else {
      return xmppEntityPool.put(
          jid,
          new RemoteClient(
              getSession(),
              (Account) getXmppEntityInstance(jid.toBareJid()),
              jid.getResourcePart())
      );
    }
  }

  @Nullable
  public LocalClient getLocalClient() {
    if (getSession().getState() != AbstractSession.State.ONLINE) {
      throw new IllegalStateException();
    }
    if (client != null) {
      return client;
    }
    return new LocalClient(getSession());
  }

  @Override
  @NonNull
  public Set<Class<? extends AbstractPlugin>> getDependencies() {
    return new HashSet<>(0);
  }

  @Override
  @NonNull
  public Set<String> getFeatures() {
    return new HashSet<>(Arrays.asList(fixedFeatures));
  }

  @Override
  @NonNull
  public Set<Map.Entry<String, String>> getSupportedStanzas() {
    return null;
  }
}