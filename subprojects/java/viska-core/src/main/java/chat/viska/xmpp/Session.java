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

import chat.viska.commons.ExceptionCaughtEvent;
import chat.viska.commons.reactive.MutableReactiveObject;
import chat.viska.commons.reactive.ReactiveObject;
import chat.viska.xmpp.plugins.BasePlugin;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XMPP session defining abstract concept for all implementations.
 */
@ThreadSafe
public abstract class Session implements AutoCloseable {

  /**
   * State of a {@link Session}.
   *
   * <h1>State diagram</h1>
   * <pre>{@code
   *             +--------------+
   *             |              |
   *             |   DISPOSED   |
   *             |              |
   *             +--------------+
   *
   *                    ^
   *                    | dispose()
   *                    +
   *
   *             +---------------+
   *             |               |                            Connection loss
   * +---------> | DISCONNECTED  |    <------------+------------------------------+-----------------------+
   * |           |               |                 |                              |                       |
   * |           +---------------+                 |                              |                       |
   * |                                             |                              |                       |
   * |                 +  ^                        |                              |                       |
   * |         login() |  | Connection loss        |                              |                       |
   * |                 v  +                        +                              +                       +
   * |
   * |           +--------------+           +--------------+               +--------------+        +--------------+
   * |           |              |           |              |               |              |        |              |
   * |           |  CONNECTING  | +-------> |  CONNECTED   | +---------->  | HANDSHAKING  | +----> |   ONLINE     |
   * |           |              |           |              |               |              |        |              |
   * |           +--------------+           +--------------+               +--------------+        +--------------+
   * |
   * |                  +                           +                             +                       +
   * |                  | disconnect()              |                             |                       |
   * |                  v                           |                             |                       |
   * |                                              |                             |                       |
   * |           +---------------+                  |                             |                       |
   * |           |               |                  |                             |                       |
   * +---------+ | DISCONNECTING | <----------------+-----------------------------+-----------------------+
   *             |               |                             disconnect()
   *             +---------------+
   * }</pre>
   */
  public enum State {

    /**
     * Indicates a network connection to the server is established and is
     * about to login or perform in-band registration.
     */
    CONNECTED,

    /**
     * Indicates the {@link Session} is establishing a network
     * connection to a server.
     */
    CONNECTING,

    /**
     * Indicates the network connection is lost and waiting to reconnect and
     * resume the XMPP stream. However, it enters {@link State#DISPOSED} directly
     * upon losing the connection if
     * <a href="https://xmpp.org/extensions/xep-0198.html">Stream
     * Management</a> is disabled.
     */
    DISCONNECTED,

    /**
     * Indicates the {@link Session} is closing the connection or the
     * server is doing so.
     */
    DISCONNECTING,

    /**
     * Indicates the {@link Session} has been shut down. Most actions
     * that changes the state will throw an {@link IllegalStateException}.
     */
    DISPOSED,

    /**
     * Indicates the {@link Session} is logging in.
     */
    HANDSHAKING,

    /**
     * Indicates the user has logged into the server.
     */
    ONLINE
  }

  /**
   * Contains information of {@link Plugin}s applied on an {@link Session}.
   */
  @ThreadSafe
  public class PluginManager implements SessionAware {

    @GuardedBy("itself")
    private final Set<PluginContext> contexts = new HashSet<>();

    private PluginManager() {}

    /**
     * Applies a {@link Plugin}. This method does nothing if the plugin
     * has already been applied.
     * @throws IllegalArgumentException If it fails to apply the {@link Plugin}.
     */
    public void apply(@Nonnull final Class<? extends Plugin> type) {
      if (getPlugin(type) != null) {
        return;
      }
      synchronized (this.contexts) {
        if (getPlugin(type) != null) {
          return;
        }
        final Plugin plugin;
        try {
          plugin = type.getConstructor().newInstance();
        } catch (Exception ex) {
          throw new IllegalArgumentException(
              ex
          );
        }
        for (Class<? extends Plugin> it : plugin.getDependencies()) {
          apply(it);
        }
        final PluginContext context = new PluginContext(plugin);
        this.contexts.add(context);
        plugin.onApplying(context);
      }
    }

    /**
     * Gets an applied plugin which is of a particular type.
     * @return {@code null} if the plugin cannot be found.
     */
    @Nullable
    public <T extends Plugin> T getPlugin(@Nonnull final Class<T> type) {
      synchronized (this.contexts) {
        for (PluginContext it : this.contexts) {
          if (type.isInstance(it.plugin)) {
            return (T) it.plugin;
          }
        }
      }
      return null;
    }

    @Nonnull
    public Set<Plugin> getPlugins() {
      final Set<Plugin> results = new HashSet<>();
      Observable.fromIterable(this.contexts).map(it -> it.plugin).subscribe(results::add);
      return Collections.unmodifiableSet(results);
    }

    public void setEnabled(@Nonnull final Class<? extends Plugin> type, final boolean enabled) {
      synchronized (this.contexts) {
        for (PluginContext it : this.contexts) {
          if (type.isInstance(it.plugin)) {
            it.enabled.changeValue(false);
          }
        }
      }
    }

    public void remove(@Nonnull final Class<? extends Plugin> type) {
      final Set<PluginContext> toRemove = new HashSet<>();
      synchronized (this.contexts) {
        for (PluginContext it : this.contexts) {
          if (type.isInstance(it.plugin)) {
            it.onDestroying();
            toRemove.add(it);
          }
        }
        this.contexts.removeAll(toRemove);
      }
    }

    @Override
    @Nonnull
    public Session getSession() {
      return Session.this;
    }
  }

  /**
   * Represents a processing window offered by a {@link Session} to a {@link Plugin}. When the
   * {@link Plugin} is disabled or the {@link Session} is not online, the context will become
   * unavailable during which no {@link Stanza} may be sent or received. The context will however
   * buffer all pending outbound {@link Stanza}s and send them all once it becomes available. When
   * the {@link Plugin} is removed from the {@link Session}, the context is destroyed and all its
   * stream properties will signal a completion.
   */
  public class PluginContext implements SessionAware {

    @GuardedBy("itself")
    private final MutableReactiveObject<Boolean> enabled = new MutableReactiveObject<>(true);
    private final MutableReactiveObject<Boolean> available = new MutableReactiveObject<>(
        enabled.getValue() && getState().getValue() == State.ONLINE
    );
    private final Plugin plugin;
    private final FlowableProcessor<Stanza> inboundStanzaStream = PublishProcessor.create();
    private final Disposable stanzaSubscription;

    private PluginContext(@Nonnull final Plugin plugin) {
      this.plugin = plugin;
      Flowable.combineLatest(
          this.enabled.getStream(),
          getState().getStream(),
          (enabled, state) -> enabled && state == State.ONLINE
      ).subscribe(this.available::changeValue);
      this.stanzaSubscription = Session.this.getInboundStanzaStream().filter(
          it -> this.enabled.getValue()
      ).subscribe(
          this.inboundStanzaStream::onNext
      );
      getState().getStream().filter(it -> it == State.DISPOSED).firstOrError().subscribe( it -> {
        onDestroying();
      });
    }

    private void onDestroying() {
      this.stanzaSubscription.dispose();
      this.inboundStanzaStream.onComplete();
      synchronized (this.enabled) {
        this.enabled.complete();
      }
      this.available.complete();
    }

    /**
     * Sends a stanza.
     */
    @Nonnull
    public StanzaReceipt sendStanza(@Nonnull final Stanza stanza) {
      this.available.getStream().filter(it -> it).firstElement().subscribe(
          it -> Session.this.sendStanza(stanza)
      );
      return new StanzaReceipt(Session.this, Maybe.empty());
    }

    /**
     * Sends an {@code <iq/>}.
     * @return {@link IqReceipt} whose {@link IqReceipt#getResponse()} will signal a completion
     *         immediately if the provided {@code iq} is a result or error.
     */
    @Nonnull
    public IqReceipt sendIq(@Nonnull final Stanza iq) {
      if (StringUtils.isBlank(iq.getId())) {
        throw new IllegalArgumentException("No ID");
      }
      final Maybe<Stanza> response;
      if (iq.getType() != Stanza.Type.IQ) {
        throw new IllegalArgumentException("Not an <iq/>.");
      } else if (iq.getIqType() == Stanza.IqType.GET || iq.getIqType() == Stanza.IqType.SET) {
        response = getInboundStanzaStream().filter(
            it -> iq.getId().equals(it.getId())
        ).firstElement().doOnSuccess(it -> {
          if (it.getIqType() == Stanza.IqType.ERROR) {
            final StanzaErrorException error;
            try {
              error = StanzaErrorException.fromXml(it.getXml());
              throw error;
            } catch (StreamErrorException ex) {
              sendError(ex);
            }
          }
        });
      } else if (iq.getIqType() == null) {
        throw new IllegalArgumentException("<iq/> has no type.");
      } else {
        response = Maybe.empty();
      }
      return new IqReceipt(Session.this, sendStanza(iq).getServerAcknowledment(), response);
    }

    /**
     * Sends a simple query. This query will look like:
     * <p>{@code <iq id="..." to="target"><query xmlns="namespace" (more params) />}</p>
     */
    @Nonnull
    public IqReceipt sendIq(@Nonnull final String namespace,
                            @Nullable final Jid recipient,
                            @Nullable final Map<String, String> attributes) {
      final String id = UUID.randomUUID().toString();
      final Document iq =  Stanza.getIqTemplate(
          Stanza.IqType.GET,
          id,
          getNegotiatedJid(),
          recipient
      );
      final Element element = (Element) iq.getDocumentElement().appendChild(
          iq.createElementNS(namespace, "query")
      );
      if (attributes != null) {
        for (Map.Entry<String, String> it : attributes.entrySet()) {
          element.setAttribute(it.getKey(), it.getValue());
        }
      }
      return sendIq(new Stanza(iq));
    }

    /**
     * Sends a stanza error.
     */
    @Nonnull
    public StanzaReceipt sendError(@Nonnull final StanzaErrorException error) {
      return sendStanza(new Stanza(error.toXml()));
    }

    public void sendError(@Nonnull final StreamErrorException error) {
      isAvailable().getStream().filter(it -> it).firstElement().subscribe(
          it -> Session.this.sendError(error)
      );
    }

    /**
     * Gets a stream of inbound {@link Stanza}s.
     */
    @Nonnull
    public Flowable<Stanza> getInboundStanzaStream() {
      return inboundStanzaStream;
    }

    public ReactiveObject<Boolean> isAvailable() {
      return available;
    }

    @Nonnull
    @Override
    public Session getSession() {
      return Session.this;
    }
  }

  private final MutableReactiveObject<State> state = new MutableReactiveObject<>(State.DISCONNECTED);
  private final FlowableProcessor<EventObject> eventStream;
  private final PluginManager pluginManager = new PluginManager();
  private final Logger logger = Logger.getAnonymousLogger();
  private Jid loginJid = Jid.EMPTY;
  private Jid authzId = Jid.EMPTY;
  private boolean neverOnline = true;

  private void onDisposing() {
    this.eventStream.onComplete();
    this.state.complete();
  }

  protected void log(final EventObject event) {
    logger.log(Level.FINE, event.toString());
  }

  protected void log(final ExceptionCaughtEvent event) {
    logger.log(Level.WARNING, event.toString(), event.getCause());
  }

  protected Session() {
    // Event stream
    final FlowableProcessor<EventObject> unsafeEventStream = PublishProcessor.create();
    this.eventStream = unsafeEventStream.toSerialized();
    getState().getStream().filter(it -> it == State.ONLINE).firstElement().subscribe(
        it -> neverOnline = false
    );
    getState().getStream().filter(
        it -> it == State.DISPOSED
    ).firstOrError().observeOn(Schedulers.io()).subscribe(
        it -> onDisposing()
    );

    // Logging
    this.eventStream.subscribe(this::log);
    getState().getStream().subscribe(it -> this.logger.fine(
        "Session is now " + it.name())
    );
  }

  /**
   * Triggers an {@link EventObject}.
   * @param event The event to be triggered.
   */
  protected void triggerEvent(@Nonnull final EventObject event) {
    if (!eventStream.hasComplete()) {
      eventStream.onNext(event);
    }
  }

  /**
   * Gets a stream of inbound {@link Stanza}s. It is usually subscribed by {@link PluginContext}s.
   */
  @Nonnull
  protected abstract Flowable<Stanza> getInboundStanzaStream();

  /**
   * Sends a {@link Stanza} into the XMPP stream. Usually invoked by {@link PluginContext}s.
   */
  protected abstract void sendStanza(@Nonnull final Stanza stanza);

  /**
   * Sends a stream error and then disconnects.
   * @throws IllegalStateException If this {@link Session} is not connected or
   *         online.
   */
  protected abstract void sendError(@Nonnull final StreamErrorException error);

  /**
   * Sets the {@link Session.State}. May also set {@link Session.State#DISPOSED} resulting in
   * calling {@link #dispose()}.
   * @throws IllegalStateException If trying to set to another {@link Session.State} when this class
   *         is already disposed of.
   */
  protected void changeState(@Nonnull final State state) {
    if (this.state.getValue() == State.DISPOSED && state != State.DISPOSED) {
      throw new IllegalStateException();
    }
    this.state.changeValue(state);
  }

  /**
   * Gets the negotiated {@link StreamFeature}s.
   */
  @Nonnull
  public abstract Set<StreamFeature> getStreamFeatures();

  /**
   * Starts shutting down the Session and releasing all system resources.
   */
  @Nonnull
  @CheckReturnValue
  @WillCloseWhenClosed
  public abstract Completable dispose();

  /**
   * Gets the negotiated {@link Jid} after handshake.
   * @return {@link Jid#EMPTY} if the handshake has not completed yet or it is an anonymous login.
   */
  @Nonnull
  public abstract Jid getNegotiatedJid();

  @Override
  public void close() {
    dispose().blockingAwait();
  }

  /**
   * Gets a stream of emitted {@link EventObject}s. It never emits any errors
   * but will emit a completion signal once this class enters
   * {@link State#DISPOSED}.
   *
   * <p>This class emits the following types of {@link EventObject}:</p>
   *
   * <ul>
   *   <li>{@link chat.viska.commons.ExceptionCaughtEvent}</li>
   * </ul>
   */
  @Nonnull
  public Flowable<EventObject> getEventStream() {
    return eventStream;
  }

  /**
   * Gets the logger.
   */
  @Nonnull
  public Logger getLogger() {
    return logger;
  }

  /**
   * Gets the current {@link State}.
   */
  @Nonnull
  public ReactiveObject<State> getState() {
    return state;
  }

  /**
   * Gets the plugin manager.
   */
  @Nonnull
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  /**
   * Sets the {@link Jid} used for login. It is by default set to {@link Jid#EMPTY} which means
   * anonymous login. This property can only be changed while disconnected and before going online
   * the first time.
   */
  public void setLoginJid(@Nullable final Jid jid) {
    if (!neverOnline || getState().getValue() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.loginJid = Jid.isEmpty(jid) ? Jid.EMPTY : jid;
  }

  /**
   * Sets the authorization identity. It is by default set to {@link Jid#EMPTY}. This property
   * can only be changed while disconnected and before going online the first time.
   */
  public void setAuthorizationId(@Nullable final Jid jid) {
    if (!neverOnline || getState().getValue() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.authzId = jid;
  }

  public Jid getLoginJid() {
    return loginJid;
  }

  public Jid getAutorizationId() {
    return authzId;
  }
}