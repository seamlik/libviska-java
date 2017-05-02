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
import chat.viska.commons.events.ExceptionCaughtEvent;
import chat.viska.commons.events.StateChangedEvent;
import chat.viska.commons.pipelines.Pipeline;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;
import org.w3c.dom.Document;

/**
 * @since 0.1
 */
public abstract class Session implements EventSource {

  public enum State {
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING,
    HANDSHAKING,
    ONLINE,
    SHUTDOWN,
  }

  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();


  private final Subject<Event> eventStream;
  private final Jid loginJid;
  private final LoggingManager loggingManager;
  private final PluginManager pluginManager;
  private ConnectionMethod connectionMethod;
  private String resource;
  private String streamId;
  private AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
  private Pipeline<Document, Document> xmlPipeline = new Pipeline<>();


  private void setState(State state) {
    State lastState = this.state.get();
    this.state.set(state);
    triggerEvent(new StateChangedEvent<>(this, lastState));
  }

  protected Session(@NonNull Jid loginJid) {
    Validate.notNull(loginJid);
    final Session thisSession = this;
    this.loginJid = loginJid.toBareJid();
    PublishSubject<Event> unsafeEventStream = PublishSubject.create();
    this.eventStream = unsafeEventStream.toSerialized();
    xmlPipeline.getInboundExceptionStream().subscribe(new Consumer<Throwable>() {
      @Override
      public void accept(Throwable cause) throws Exception {
        triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
      }
    });
    xmlPipeline.getOutboundExceptionStream().subscribe(new Consumer<Throwable>() {
      @Override
      public void accept(Throwable cause) throws Exception {
        triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
      }
    });
    this.loggingManager = new LoggingManager(this);
    this.pluginManager = new PluginManager(this);
  }

  protected abstract @NonNull Future<Void> sendStreamOpening();

  protected abstract @NonNull Future<Void> sendStreamClosing();

  protected abstract @NonNull void onOpeningConnection()
      throws ConnectionException, InterruptedException;

  protected abstract @NonNull void onClosingConnection();

  protected void triggerEvent(Event event) {
    loggingManager.log(event, null);
    eventStream.onNext(event);
  }

  protected Pipeline<Document, Document> getXmlPipeline() {
    return xmlPipeline;
  }

  public abstract boolean isCompressionEnabled();

  public abstract void enableCompression(boolean enabled);

  public Future<Void> connect(@NonNull String password)
      throws ConnectionMethodNotFoundException, ConnectionException {
    Validate.notBlank(password);
    switch (state.get()) {
      case HANDSHAKING:
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
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        onOpeningConnection();
        setState(State.HANDSHAKING);
        xmlPipeline.start();
        return null;
      }
    });
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
    return THREAD_POOL_INSTANCE.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        sendStreamClosing().get();
        setState(State.DISCONNECTED);
        onClosingConnection();
        setState(State.SHUTDOWN);
        return null;
      }
    });
  }


  public void shutdown() {
    //TODO
  }

  public void send(@NonNull Document xml) {
    xmlPipeline.write(xml);
  }


  public ConnectionMethod getConnectionMethod() {
    return connectionMethod;
  }

  public void setConnectionMethod(ConnectionMethod connectionMethod) {
    this.connectionMethod = connectionMethod;
  }

  public Observable<Document> getInboundStanzaStream() {
    return xmlPipeline.getInboundStream();
  }

  public Observable<Document> getOutboundXmlStream() {
    return xmlPipeline.getOutboundStream();
  }

  public LoggingManager getLoggingManager() {
    return loggingManager;
  }

  public Jid getLoginJid() {
    return loginJid;
  }

  public PluginManager getPluginManager() {
    return pluginManager;
  }

  public State getState() {
    return state.get();
  }

  public void setResource(String resource) {

  }

  @Override
  public Subject<Event> getEventStream() {
    return eventStream;
  }
}