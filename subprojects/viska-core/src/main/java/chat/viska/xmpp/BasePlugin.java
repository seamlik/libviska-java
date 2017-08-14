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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Provides the most fundamental features of an XMPP session.. This plugin is
 * built-in and needs be applied manually.
 *
 * <h1>Supported XMPP Extensions</h1>
 * <ul>
 *   <li><a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a></li>
 *   <li><a href="https://xmpp.org/extensions/xep-0092.html">XEP-0092: Software Version</a></li>
 * </ul>
 */
public class BasePlugin implements Plugin {

  static final String XMLNS_SOFTWARE_VERSION = "jabber:iq:version";

  private static final String[] fixedFeatures = new String[] {
      XMLNS_SOFTWARE_VERSION
  };

  private final Session session;
  private final Map<Jid, AbstractEntity> xmppEntityPool = new HashMap<>();
  private final LocalClient localClient;

  public BasePlugin(final @NonNull Session session) {
    Objects.requireNonNull(session);
    this.session = session;
    this.localClient = new LocalClient(session);
  }

  @Nullable
  public AbstractEntity getXmppEntityInstance(@NonNull final Jid jid) {
    if (session.getState() == Session.State.DISPOSED) {
      throw new IllegalStateException("Session disposed.");
    }
    Objects.requireNonNull(jid);
    synchronized (xmppEntityPool) {
      AbstractEntity entity = xmppEntityPool.get(jid);
      if (entity != null) {
        return entity;
      } else if (jid.getLocalPart().isEmpty()) {
        entity = new Server(getSession(), jid);
      } else if (jid.getResourcePart().isEmpty()) {
        entity = new Account(session, jid);
      } else if (jid.equals(this.session.getJid())) {
        entity = this.localClient;
      } else {
        entity = new RemoteClient(session, jid);
      }
      xmppEntityPool.put(jid, entity);
      return entity;
    }
  }

  @Nullable
  public LocalClient getLocalClient() {
    return localClient;
  }

  @Override
  @NonNull
  public Set<Class<? extends Plugin>> getDependencies() {
    return new HashSet<>(0);
  }

  @Override
  @NonNull
  public Set<String> getDiscoFeatures() {
    return new HashSet<>(Arrays.asList(fixedFeatures));
  }

  @Override
  @NonNull
  public Set<Map.Entry<String, String>> getSupportedStanzas() {
    return new HashSet<>(0);
  }

  @Override
  @NonNull
  public Session getSession() {
    return session;
  }
}