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
import chat.viska.xmpp.Stanza;
import chat.viska.xmpp.plugins.base.BasePlugin;
import io.reactivex.Completable;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.EventObject;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import rxbeans.StandardObject;
import rxbeans.StandardProperty;

/**
 * Provides support for <a href="https://xmpp.org/extensions/xep-0166.html">Jingle</a> sessions.
 */
@Plugin.DependsOn(BasePlugin.class)
@Plugin.Features(CommonXmlns.JINGLE)
@ThreadSafe
public class JinglePlugin extends StandardObject implements Plugin {

  /**
   * Indicates the plugin received a Jingle session initiation.
   */
  @ThreadSafe
  public class SessionInitiationReceivedEvent extends EventObject {

    private final Session session;
    private final Stanza stanza;

    private SessionInitiationReceivedEvent(final Session session, final Stanza stanza) {
      super(JinglePlugin.this);
      this.session = session;
      this.stanza = stanza;
    }

    /**
     * Gets the {@link Session} to be created.
     */
    public Session getSession() {
      return session;
    }

    /**
     * Accepts the {@link Session}.
     * @return Token that notifies once the initiator sends an ACK.
     */
    public Completable accept() {
      throw new UnsupportedOperationException();
    }

    /**
     * Rejects the {@link Session} but sends a redirect message.
     */
    public void redirect(final String msg) {
      throw new UnsupportedOperationException();
    }

    /**
     * Rejects the {@link Session}.
     */
    public void reject() {
      throw new UnsupportedOperationException();
    }
  }

  private final StandardProperty<Map<String, Session>> sessions = new StandardProperty<>(
      Collections.emptyMap()
  );
  private chat.viska.xmpp.Session.@MonotonicNonNull PluginContext context;

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

  public Session initiateRtpSession(final Jid responder, final boolean audio, final boolean video) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return Collections.singleton(
        new AbstractMap.SimpleImmutableEntry<>(CommonXmlns.JINGLE, "jingle")
    );
  }

  @Override
  public void onApplying(chat.viska.xmpp.Session.PluginContext context) {
    this.context = context;
  }
}