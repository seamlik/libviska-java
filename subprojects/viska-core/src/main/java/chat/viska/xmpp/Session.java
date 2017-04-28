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

import chat.viska.commons.events.Event;
import chat.viska.commons.events.EventSource;
import chat.viska.commons.events.StateChangedEvent;
import chat.viska.commons.pipelines.Pipeline;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;

/**
 * @since 0.1
 */
public abstract class Session implements EventSource {

  public enum State {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING,
    ONLINE,
    SHUTDOWN,
  }

  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();

  private AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
  private final Jid loginJid;
  private final PluginManager pluginManager;
  private final Subject<Event> eventStream;
  private ConnectionMethod connectionMethod;
  private final LoggingManager loggingManager;
  private String streamId;
  private Pipeline<String, String> xmlPipeline = new Pipeline<>();

  private void setState(State state) {
    State lastState = this.state.get();
    this.state.set(state);
    triggerEvent(new StateChangedEvent<>(this, lastState));
  }

  protected Session(@NonNull Jid loginJid) {
    Validate.notNull(loginJid, "`loginJid` must not be null");
    this.loginJid = loginJid.toBareJid();
    PublishSubject<Event> unsafeEventStream = PublishSubject.create();
    this.eventStream = unsafeEventStream.toSerialized();
    this.loggingManager = new LoggingManager(this);
    this.pluginManager = new PluginManager(this);
  }

  protected abstract @NonNull String generateStreamOpening();

  protected abstract @NonNull String generateStreamClosing();

  protected abstract @NonNull Future<Void> onOpeningConnection() throws ConnectionException;

  protected abstract @NonNull Future<Void> onClosingConnection();

  protected void triggerEvent(Event event) {
    loggingManager.log(event, null);
    eventStream.onNext(event);
  }

  protected Pipeline<String, String> getXmlPipeline() {
    return xmlPipeline;
  }

  public abstract @NonNull Future<List<ConnectionMethod>> queryConnectionMethod();

  public void send(@NonNull String xml) {
    xmlPipeline.write(xml);
  }

  public Observable<String> getInboundXmppStream() {
    return xmlPipeline.getInboundStream();
  }

  public Observable<String> getOutboundXmppStream() {
    return xmlPipeline.getOutboundStream();
  }

  public Future<Void> connect(@NonNull String password, @Nullable String resource)
      throws ConnectionMethodNotFoundException, ConnectionException {
    Validate.notBlank(password);
    switch (state.get()) {
      case CONNECTED:
        return ConcurrentUtils.constantFuture(null);
      case CONNECTING:
        return ConcurrentUtils.constantFuture(null);
      case DISCONNECTED:
        throw new IllegalStateException("Client is disconnecting.");
      case ONLINE:
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
    xmlPipeline.start();
    final Future<Void> result = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        onOpeningConnection().get();
        setState(State.CONNECTED);
        return null;
      }
    });
    return result;
  }

  public Future<Void> connect(@NonNull String password)
      throws ConnectionException, ConnectionMethodNotFoundException {
    return connect(password, null);
  }

  public Future<Void> disconnect() {
    switch (state.get()) {
      case DISCONNECTED:
        return ConcurrentUtils.constantFuture(null);
      case DISCONNECTING:
        return ConcurrentUtils.constantFuture(null);
      case CONNECTING:
        throw new IllegalStateException();
      default:
        break;
    }
    FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        send(generateStreamClosing());
        setState(State.DISCONNECTED);
        onClosingConnection().get();
        setState(State.SHUTDOWN);
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  public State getState() {
    return state.get();
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

  public void shutdown() {
    //TODO
  }

  public ConnectionMethod getConnectionMethod() {
    return connectionMethod;
  }

  public void setConnectionMethod(ConnectionMethod connectionMethod) {
    this.connectionMethod = connectionMethod;
  }

  @Override
  public Subject<Event> getEventStream() {
    return eventStream;
  }
}