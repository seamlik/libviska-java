/*
 * Copyright 2018 Kai-Chung Yan (殷啟聰)
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

import chat.viska.xmpp.Jid;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import rxbeans.Property;
import rxbeans.StandardObject;

/**
 * <a href="https://xmpp.org/extensions/xep-0166.html">Jingle</a> session.
 */
@ThreadSafe
public abstract class Session extends StandardObject {

  public static class Description {

    public final Map<String, Content.Description> contents;
    public final Set<ContentGroup> groups;

    public Description(Map<String, Content.Description> contents, Set<ContentGroup> groups) {
      this.contents = Collections.unmodifiableMap(new HashMap<>(contents));
      this.groups = Collections.unmodifiableSet(new HashSet<>(groups));
    }
  }

  /**
   * State of a {@link Session}.
   *
   * <pre>{@code
   *            modify contents                              createOffer()                    applyRemoteDescription()
   *                                  +------------------+                    +------------+
   *      +-------------------------> | PREPARING_OFFER  | +--------------->  | OFFER_SENT | +-------------------------+
   *      |                           +------------------+                    +------------+                           |
   *      |                                                                                                            |
   *      |                                    +                                    +                                  |
   *      |                                    | terminate()                        |                                  |
   *      +                                    v                                    |                                  v
   *                  terminate()                                                   | terminate()
   * +---------+                         +------------+                             |                            +------------+
   * | CREATED | +---------------------> | TERMINATED |  <-----------------------------------------------------+ |            |
   * +---------+                         +------------+                             |                            |            |
   *                                                                                +                            | NEGOTIATED |
   *      +                                    ^                                                                 |            |
   *      |                                    |                                +--------+                       |            |
   *      |                                    | terminate()                    | ACTIVE | <-------------------+ |            |
   *      |                                    |                                +--------+                       +------------+
   *      |                                    +                                               Wait for ICE
   *      |                                                                                                            ^
   *      |                           +------------------+                                                             |
   *      +-------------------------> | PREPARING_ANSWER | +-----------------------------------------------------------+
   *                                  +------------------+                      createAnswer()
   *       applyRemoteDescription()
   * }</pre>
   */
  public enum State {

    /**
     * Media is flowing between peers.
     */
    ACTIVE,

    /**
     * {@link Session} has just been created.
     */
    CREATED,

    /**
     * Previously active but now disconnected accidentally.
     */
    DISCONNECTED,

    /**
     * An offer is generated and might have been sent to the peer, waiting for an answer.
     */
    OFFER_SENT,

    /**
     * Preparing an answer.
     */
    PREPARING_ANSWER,

    /**
     * Preparing an offer.
     */
    PREPARING_OFFER,

    /**
     * Offer and answer have been exchanged but ICE gathering might have not been completed.
     */
    NEGOTIATED,

    /**
     * Terminated.
     */
    TERMINATED
  }

  public class TerminatedWithErrorEvent extends EventObject {

    private final Exception cause;

    public TerminatedWithErrorEvent(final Exception cause) {
      super(Session.this);
      this.cause = cause;
    }

    public Exception getCause() {
      return cause;
    }
  }

  private final String name;
  private final Jid peer;

  public Session(final String name, final Jid peer) {
    this.name = name;
    this.peer = peer;
  }

  public abstract Set<? extends Content> getLocalContents();

  public abstract void applyRemoteDescription(Description description);

  public abstract Description createOffer();

  public abstract Description createAnswer();

  public abstract Property<State> stateProperty();

  public abstract void terminate();

  public abstract void reconnect();

  public String getName() {
    return name;
  }

  public Jid getPeer() {
    return peer;
  }
}