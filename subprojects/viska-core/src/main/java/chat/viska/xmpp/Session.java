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

import chat.viska.Event;
import chat.viska.EventSource;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.w3c.dom.Document;

/**
 * @since 0.1
 */
public abstract class Session implements EventSource {

  public enum State {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING,
    OFFLINE,
    ONLINE,
    SHUTDOWN,
  }

  public static class StateChangedEvent extends Event {

    private State lastState;

    public StateChangedEvent(@NonNull EventSource source, @NonNull State lastState) {
      super(source, Level.FINE, null);
      this.lastState = lastState;
    }

    public State getLastState() {
      return lastState;
    }
  }

  public static class ExceptionCaughtEvent extends Event {

    private Throwable cause;

    public ExceptionCaughtEvent(@NonNull EventSource source, @NonNull Throwable cause) {
      super(source, Level.WARNING, null);
      Validate.notNull(cause, "`cause` must not be null.");
      this.cause = cause;
    }

    public Throwable getCause() {
      return cause;
    }
  }

  private State state = State.DISCONNECTED;
  private Jid loginJid;
  private final PluginManager pluginManager = new PluginManager(this);
  private Subject<Event> eventStream = PublishSubject.create();
  private Proxy proxy;
  private ConnectionMethod connectionMethod;
  private final LoggingManager loggingManager;

  private synchronized void setState(State state) {
    State lastState = this.state;
    this.state = state;
    triggerEvent(new StateChangedEvent(this, lastState));
  }

  protected Session(@NonNull Jid loginJid) {
    Validate.notNull(loginJid, "`loginJid` must not be null");
    this.loginJid = loginJid.toBareJid();
    this.loggingManager = new LoggingManager(this);
  }

  protected abstract void onOpeningXmppStream();

  protected abstract void onClosingXmppStream();

  protected abstract Future<Void> onConnecting()
      throws ConnectionMethodNotFoundException, ConnectionException;

  protected abstract void onDisconnecting();

  protected abstract void onShuttingDown();

  protected synchronized void triggerEvent(Event event) {
    loggingManager.log(event, null);
    eventStream.onNext(event);
  }

  public abstract Future<Void> send(@NonNull String xml);

  public Future<Void> send(@NonNull Document xml) {
    throw new RuntimeException();
  }

  public abstract Observable<String> getInboundXmppStream();

  public abstract Observable<String> getOutboundXmppStream();

  public abstract @NonNull Future<List<ConnectionMethod>> queryConnectionMethod();

  public synchronized Future<Void> connect()
      throws ConnectionMethodNotFoundException, ConnectionException {
    switch (state) {
      case CONNECTED:
        return ConcurrentUtils.constantFuture(null);
      case CONNECTING:
        return ConcurrentUtils.constantFuture(null);
      case DISCONNECTED:
        throw new IllegalStateException("Client is disconnecting.");
      case ONLINE:
        return ConcurrentUtils.constantFuture(null);
      case OFFLINE:
        return ConcurrentUtils.constantFuture(null);
      case SHUTDOWN:
        throw new IllegalStateException("Client has been shut down.");
      default:
        break;
    }
    if (connectionMethod == null) {
      throw new ConnectionMethodNotFoundException();
    }
    setState(State.CONNECTING);
    return onConnecting();
  }

  public synchronized void disconnect() {
    switch (state) {
      case DISCONNECTED:
        return;
      case DISCONNECTING:
        return;
      case CONNECTING:
        throw new IllegalStateException();
    }
    //TODO
  }

  public @Nullable Proxy getProxy() {
    return proxy;
  }

  public void setProxy(Proxy proxy) {
    if (state != State.DISCONNECTED) {
      throw new IllegalStateException();
    }
    this.proxy = proxy;
  }

  public State getState() {
    return state;
  }

  public Jid getLoginJid() {
    return loginJid;
  }

  public void setResource(String resource) {

  }

  public PluginManager getPluginManager() {
    return pluginManager;
  }

  public LoggingManager getLoggingManager() {
    return loggingManager;
  }

  public void login(@NonNull String password) {
    login(password, null);
  }

  public void login(@NonNull String password, @Nullable String resource) {

  }

  public synchronized void shutdown() {
    //TODO
  }

  public ConnectionMethod getConnectionMethod() {
    return connectionMethod;
  }

  public synchronized void setConnectionMethod(ConnectionMethod connectionMethod) {
    this.connectionMethod = connectionMethod;
  }

  @Override
  public Subject<Event> getEventStream() {
    return eventStream;
  }
}