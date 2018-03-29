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

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.concurrent.ThreadSafe;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.lock.qual.GuardedBy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import rxbeans.ExceptionCaughtEvent;
import rxbeans.MutableProperty;
import rxbeans.Property;
import rxbeans.StandardObject;
import rxbeans.StandardProperty;

/**
 * XMPP session defining abstract concept for all implementations.
 *
 * <p>This type emits the following types of {@link EventObject}:</p>
 *
 * <ul>
 *   <li>{@link ExceptionCaughtEvent}</li>
 * </ul>
 */
@ThreadSafe
public abstract class Session extends StandardObject implements AutoCloseable {

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
   *                    | close()
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

    @GuardedBy("this")
    private final Set<PluginContext> contexts = new LinkedHashSet<>();

    private PluginManager() {
      final Consumer<Stanza> action = stanza -> {
        final IqSignature signature = stanza.getIqSignature();
        final List<PluginContext> interestedContexts = contexts
            .parallelStream()
            .filter(it -> it.plugin.getSupportedIqs().contains(signature))
            .collect(Collectors.toList());
        if (interestedContexts.isEmpty()) {
          sendStanza(new XmlWrapperStanza(XmlWrapperStanza.createIqError(
              stanza,
              StanzaErrorException.Condition.SERVICE_UNAVAILABLE,
              StanzaErrorException.Type.CANCEL,
              ""
          )));
        } else {
          interestedContexts.parallelStream().forEach(
              it -> it.feedStanza(stanza)
          );
        }
      };
      final Consumer<Throwable> handler = it -> {
        if (it instanceof StreamErrorException) {
          sendError((StreamErrorException) it);
        } else if (it instanceof Exception){
          log(new ExceptionCaughtEvent(Session.this, (Exception) it));
        }
      };
      getInboundStanzaStream()
          .filter(it -> it.getType() == Stanza.Type.IQ)
          .subscribeOn(Schedulers.io())
          .subscribe(action, handler);
    }

    /**
     * Applies a {@link Plugin}. This method does nothing if the plugin
     * has already been applied.
     * @throws IllegalArgumentException If it fails to apply the {@link Plugin}.
     */
    public synchronized void apply(final Class<? extends Plugin> type) {
      if (getPlugins().parallelStream().anyMatch(type::isInstance)) {
        return;
      }
      final Plugin plugin;
      try {
        plugin = type.getConstructor().newInstance();
      } catch (Exception ex) {
        throw new IllegalArgumentException(ex);
      }
      for (Class<? extends Plugin> it : plugin.getAllDependencies()) {
        apply(it);
      }
      final PluginContext context = new PluginContext(plugin);
      this.contexts.add(context);
      plugin.onApplying(context);
    }

    /**
     * Gets an applied plugin which is of a particular type.
     * @throws NoSuchElementException If no such plugin found.
     */
    public synchronized  <T extends Plugin> T getPlugin(final Class<T> type) {
      for (PluginContext it : this.contexts) {
        if (type.isInstance(it.plugin)) {
          return type.cast(it.plugin);
        }
      }
      throw new NoSuchElementException();
    }

    /**
     * Gets all applied {@link Plugin}.
     */
    public synchronized Set<Plugin> getPlugins() {
      final Set<Plugin> results = new HashSet<>();
      Observable.fromIterable(this.contexts).map(it -> it.plugin).subscribe(results::add);
      return Collections.unmodifiableSet(results);
    }

    /**
     * Enable or disable a {@link Plugin}.
     */
    public synchronized void setEnabled(final Class<? extends Plugin> type, final boolean enabled) {
      for (PluginContext it : contexts) {
        if (type.isInstance(it.plugin)) {
          it.enabled.change(false);
        }
      }
    }

    /**
     * Removes a {@link Plugin}.
     */
    public synchronized void remove(final Class<? extends Plugin> type) {
      final Set<PluginContext> toRemove = new HashSet<>();
      for (PluginContext it : contexts) {
        if (type.isInstance(it.plugin)) {
          it.onRemove();
          toRemove.add(it);
        }
      }
      contexts.removeAll(toRemove);
    }

    /**
     * Gets all features provided by the currently applied plugins.
     * @see <a href="https://xmpp.org/extensions/xep-0030.html">Service Discovery</a>
     */
    public synchronized Set<String> getAllFeatures() {
      final Set<String> features = new LinkedHashSet<>();
      for (PluginContext it : contexts) {
        features.addAll(it.plugin.getAllFeatures());
      }
      return features;
    }

    @Override
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
  @ThreadSafe
  public class PluginContext implements SessionAware {

    private final MutableProperty<Boolean> enabled = new StandardProperty<>(true);
    private final MutableProperty<Boolean> available = new StandardProperty<>(
        enabled.get() && stateProperty().get() == State.ONLINE
    );
    private final Plugin plugin;
    private final FlowableProcessor<Stanza> inboundIqStream = PublishProcessor
        .<Stanza>create()
        .toSerialized();
    private Disposable availableSubscription = Flowable.combineLatest(
        enabled.getStream(),
        stateProperty().getStream(),
        (enabled, state) -> enabled && state == State.ONLINE
    ).observeOn(Schedulers.io()).subscribe(available::change);

    private PluginContext(final Plugin plugin) {
      this.plugin = plugin;
    }

    private void onRemove() {
      availableSubscription.dispose();
    }

    private void feedStanza(final Stanza stanza) {
      inboundIqStream.onNext(stanza);
    }

    /**
     * Sends a stanza.
     */
    public StanzaReceipt sendStanza(final Stanza stanza) {
      this.available.getStream().filter(it -> it).firstElement().subscribe(
          it -> Session.this.sendStanza(stanza)
      );
      return new StanzaReceipt(Maybe.empty());
    }

    /**
     * Sends an {@code <iq/>}.
     */
    public IqReceipt sendIq(final Stanza iq) {
      if (StringUtils.isBlank(iq.getId())) {
        throw new IllegalArgumentException("No ID");
      }
      final Maybe<Stanza> response;
      if (iq.getType() != Stanza.Type.IQ) {
        throw new IllegalArgumentException("Not an <iq/>.");
      } else if (iq.getIqType() == Stanza.IqType.GET || iq.getIqType() == Stanza.IqType.SET) {
        response = getInboundIqStream().filter(
            it -> iq.getId().equals(it.getId())
        ).firstElement().doOnSuccess(it -> {
          if (it.getIqType() == Stanza.IqType.ERROR) {
            try {
              throw StanzaErrorException.fromXml(it.toXml());
            } catch (StreamErrorException ex) {
              sendError(ex);
              throw ex;
            }
          }
        });
      } else if (iq.getIqType() == null) {
        throw new IllegalArgumentException("<iq/> has no type.");
      } else {
        response = Maybe.empty();
      }
      return new IqReceipt(sendStanza(iq).getServerAcknowledment(), response);
    }

    /**
     * Sends a simple query. This query will look like:
     * <p>{@code <iq id="..." to="target"><query xmlns="namespace" (more params) />}</p>
     */
    public IqReceipt sendIq(final String namespace,
                            final Jid recipient,
                            final Map<String, String> attributes) {
      final String id = UUID.randomUUID().toString();
      final Document iq =  XmlWrapperStanza.createIq(
          Stanza.IqType.GET,
          id,
          getNegotiatedJid(),
          recipient
      );
      final Element element = (Element) iq.getDocumentElement().appendChild(
          iq.createElementNS(namespace, "query")
      );
      for (Map.Entry<String, String> it : attributes.entrySet()) {
        element.setAttribute(it.getKey(), it.getValue());
      }
      return sendIq(new XmlWrapperStanza(iq));
    }

    /**
     * Sends a stanza error.
     */
    public StanzaReceipt sendError(final StanzaErrorException error) {
      return sendStanza(new XmlWrapperStanza(error.toXml()));
    }

    /**
     * Sends a stream error and closes the connection.
     */
    public void sendError(final StreamErrorException error) {
      availableProperty().getStream().filter(it -> it).firstElement().subscribe(
          it -> Session.this.sendError(error)
      );
    }

    /**
     * Gets a stream of inbound {@code <iq/>}s that only matches the {@link IqSignature}s registered
     * in {@link Plugin#getSupportedIqs()}.
     */
    public Flowable<Stanza> getInboundIqStream() {
      return inboundIqStream;
    }

    /**
     * Indicates if the window for sending or receiving {@link Stanza}s is open.
     */
    public Property<Boolean> availableProperty() {
      return available;
    }

    @Override
    public Session getSession() {
      return Session.this;
    }
  }

  private final MutableProperty<State> state = new StandardProperty<>(State.DISCONNECTED);
  private final PluginManager pluginManager = new PluginManager();
  private final Logger logger = Logger.getAnonymousLogger();
  private Jid loginJid = Jid.EMPTY;
  private Jid authzId = Jid.EMPTY;
  private boolean neverOnline = true;

  /**
   * Performs cleaning up resources. This method is called by {@link Session} when it is being
   * closed.
   */
  protected abstract void onDisposing();

  protected void log(final EventObject event) {
    logger.log(Level.INFO, event.toString());
  }

  protected void log(final ExceptionCaughtEvent event) {
    logger.log(Level.SEVERE, event.toString(), event.getException());
  }

  protected Session() {
    state.getStream().filter(it -> it == State.ONLINE).firstElement().subscribe(
        it -> neverOnline = false
    );

    // Logging
    getEventStream().observeOn(Schedulers.io()).subscribe(this::log);
    state.getStream().subscribe(it -> this.logger.fine(
        "Session is now " + it.name())
    );
  }

  /**
   * Gets a stream of inbound {@link Stanza}s. It is usually subscribed by {@link PluginContext}s.
   */
  protected abstract Flowable<Stanza> getInboundStanzaStream();

  /**
   * Sends a {@link Stanza} into the XMPP stream. Usually invoked by {@link PluginContext}s.
   */
  protected abstract void sendStanza(final Stanza stanza);

  /**
   * Sends a stream error and then disconnects. Since a {@link Session} with a stream error is quite
   * fragile, this method won't complain even if it is disconnected already.
   */
  protected abstract void sendError(final StreamErrorException error);

  /**
   * Changes the state to {@link State#DISCONNECTED}.
   * @throws IllegalStateException If the session is disposed.
   */
  protected final void changeStateToDisconnected() {
    state.getAndDo(state -> {
      if (state == State.DISPOSED) {
        throw new IllegalStateException();
      }
      this.state.change(State.DISCONNECTED);
    });
  }

  /**
   * Changes the state to {@link State#CONNECTED}.
   * @throws IllegalStateException If the current state if not {@link State#CONNECTING}.
   */
  protected final void changeStateToConnected() {
    state.getAndDo(state -> {
      if (state != State.CONNECTING) {
        throw new IllegalStateException();
      }
      this.state.change(State.CONNECTED);
    });
  }

  /**
   * Changes the state to {@link State#CONNECTING}.
   * @throws IllegalStateException If the current state if not {@link State#DISCONNECTED}.
   */
  protected final void changeStateToConnecting() {
    state.getAndDo(state -> {
      if (state != State.DISCONNECTED) {
        throw new IllegalStateException();
      }
      this.state.change(State.CONNECTING);
    });
  }

  /**
   * Changes the state to {@link State#HANDSHAKING}.
   * @throws IllegalStateException If the current state if not {@link State#CONNECTED}.
   */
  protected final void changeStateToHandshaking() {
    state.getAndDo(state -> {
      if (state != State.CONNECTED) {
        throw new IllegalStateException();
      }
      this.state.change(State.HANDSHAKING);
    });
  }

  /**
   * Changes the state to {@link State#ONLINE}.
   */
  protected final void changeStateToOnline() {
    state.getAndDo(state -> {
      if (state == State.DISPOSED || state != State.HANDSHAKING) {
        throw new IllegalStateException();
      }
      this.state.change(State.ONLINE);
    });
  }

  /**
   * Gets the negotiated {@link StreamFeature}s.
   */
  public abstract Set<StreamFeature> getStreamFeatures();

  /**
   * Gets the negotiated {@link Jid} after handshake.
   * @return {@link Jid#EMPTY} if the handshake has not completed yet or it is an anonymous login.
   */
  public abstract Jid getNegotiatedJid();

  /**
   * Starts closing the XMPP stream and the network connection.
   */
  public void disconnect() {
    stateProperty().getAndDo(state -> {
      if (state == State.DISCONNECTED || state == State.DISCONNECTING || state == State.DISPOSED) {
        return;
      }
      this.state.change(State.DISCONNECTING);
    });

  }

  /**
   * Gets the logger.
   */
  public final Logger getLogger(@UnknownInitialization(Session.class) Session this) {
    return logger;
  }

  /**
   * Gets the current {@link State}.
   */
  public final Property<State> stateProperty(@UnknownInitialization(Session.class) Session this) {
    return state;
  }

  /**
   * Gets the plugin manager.
   */
  public PluginManager getPluginManager() {
    return pluginManager;
  }

  /**
   * Sets the {@link Jid} used for login. It is by default set to {@link Jid#EMPTY} which means
   * anonymous login. This property can only be changed while disconnected and before going online
   * the first time.
   */
  public void setLoginJid(final Jid jid) {
    if (!neverOnline || stateProperty().get() != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.loginJid = Jid.isEmpty(jid) ? Jid.EMPTY : jid;
  }

  /**
   * Sets the authorization identity. It is by default set to {@link Jid#EMPTY}. This property
   * can only be changed while disconnected and before going online the first time.
   */
  public void setAuthorizationId(final Jid jid) {
    if (!neverOnline || stateProperty().get() != State.DISCONNECTED) {
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

  @Override
  public final void close() {
    state.getAndDo(state -> {
      switch (state) {
        case DISPOSED:
          return;
        case DISCONNECTED:
          break;
        case DISCONNECTING:
          break;
        default:
          disconnect();
          break;
      }
      onDisposing();
      stateProperty()
          .getStream()
          .filter(it -> it == State.DISCONNECTED)
          .firstOrError()
          .toCompletable()
          .doOnComplete(() -> {
            onDisposing();
            this.state.change(State.DISPOSED);
          })
          .observeOn(Schedulers.io())
          .subscribe();
    });
  }
}