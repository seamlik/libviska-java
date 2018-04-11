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

package chat.viska.xmpp.plugins.jingle;

import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Jid;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.plugins.base.BasePlugin;
import io.reactivex.Completable;
import java.util.Collections;
import java.util.EventObject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.concurrent.ThreadSafe;
import javax.xml.namespace.QName;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import rxbeans.Property;
import rxbeans.StandardObject;
import rxbeans.StandardProperty;

/**
 * Provides support for <a href="https://xmpp.org/extensions/xep-0166.html">Jingle</a> sessions.
 */
@Plugin.DependsOn(BasePlugin.class)
@Plugin.Features({
    CommonXmlns.JINGLE,
    CommonXmlns.JINGLE_GROUPING
})
@ThreadSafe
public class JinglePlugin extends StandardObject implements Plugin {

  /**
   * Indicates the plugin received a Jingle session initiation.
   */
  @ThreadSafe
  public class SessionInitiationReceivedEvent extends EventObject {

    private final Jid initiator;
    private final String id;
    private final Session.Description description;

    private SessionInitiationReceivedEvent(final Jid initiator,
                                           final String id,
                                           final Session.Description description) {
      super(JinglePlugin.this);
      this.initiator = initiator;
      this.id = id;
      this.description = description;
    }

    public Session.Description getDescription() {
      return description;
    }

    public Jid getInitiator() {
      return initiator;
    }

    public String getId() {
      return id;
    }

    /**
     * Accepts the {@link Session}.
     * @return Token that notifies once the initiator sends an ACK.
     */
    public Completable accept() {
      throw new UnsupportedOperationException();
    }

    /**
     * Rejects the {@link Session}.
     */
    public void reject() {
      throw new UnsupportedOperationException();
    }
  }

  public static QName JINGLE_QNAME = new QName(
      CommonXmlns.JINGLE, "jingle"
  );
  private chat.viska.xmpp.Session.@MonotonicNonNull PluginContext context;

  private chat.viska.xmpp.Session.PluginContext getContext() {
    if (context == null) {
      throw new IllegalStateException();
    }
    return context;
  }

  public Property<Map<String, Session>> sessionsProperty() {
    return sessions;
  }

  /**
   * Initiates a {@link Session}.
   * @return Token that signals a completion if the peer accepts the {@link Session} or signals an
   *         {@link chat.viska.xmpp.StanzaErrorException} if the peer rejects.
   */
  public Completable initiateSession(final Session session) {
    throw new UnsupportedOperationException();
  }

  /**
   * Terminates a currently active {@link Session}.
   * @return Token that notifies when the peer sends an ACK.
   */
  public Completable terminateSession(final Session session) {
    throw new UnsupportedOperationException();
  }

  public Session createSession(final Jid peer) {
    return getContext()
        .getSession()
        .getPluginManager()
        .getPlugins()
        .parallelStream()
        .filter(it -> it instanceof SessionPlugin)
        .map(it -> (SessionPlugin) it)
        .findFirst()
        .orElseThrow(UnsupportedOperationException::new)
        .createSession(
            UUID.randomUUID().toString(),
            peer
        );
  }

  @Override
  public Set<QName> getSupportedIqs() {
    return Collections.singleton(JINGLE_QNAME);
  }

  @Override
  public void onApply(chat.viska.xmpp.Session.PluginContext context) {
    this.context = context;
  }
}