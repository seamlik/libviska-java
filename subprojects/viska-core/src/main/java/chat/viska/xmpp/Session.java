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
import chat.viska.OperationNotReadyException;
import io.reactivex.Observable;
import io.reactivex.Scheduler;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.net.Proxy;
import java.util.logging.Logger;
import org.joda.time.Instant;
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
    ONLINE
  }

  public static class StateChangedEvent extends Event {

    private State lastState;

    public StateChangedEvent(@NonNull State lastState, @Nullable Instant triggeredTime) {
      super(triggeredTime, null);
      this.lastState = lastState;
    }

    public StateChangedEvent(@NonNull State lastState) {
      this(lastState, null);
    }

    public State getLastState() {
      return lastState;
    }
  }

  public static final int DEFAULT_TCP_PORT = 5222;

  private State state = State.DISCONNECTED;
  private String domain;
  private String username;
  private final PluginManager pluginManager = new PluginManager(this);
  private Subject<Event> eventStream = PublishSubject.create();
  private Logger logger;

  protected Session(@NonNull String domain, @Nullable String username) {
    this.domain = domain;
    this.username = username;
    logger = Logger.getLogger(
        "chat.viska.xmpp.Session_"+ username + "@" + domain
    );
  }

  protected void triggerEvent(Event event) {
    eventStream.onNext(event);
  }

  protected void setState(State state) {
    State lastState = this.state;
    this.state = state;
    eventStream.onNext(new StateChangedEvent(lastState));
  }

  public abstract @Nullable Proxy getProxy();

  public abstract void setProxy(@Nullable Proxy proxy) throws OperationNotReadyException;

  public abstract @Nullable String getConnectionEndpoint();

  public abstract void setConnectionEndpoint(String connectionEndpoint) throws OperationNotReadyException;

  public abstract int getPort();

  public abstract void setPort(int port) throws OperationNotReadyException;

  public abstract void send(String stanza);

  public void send(@NonNull Document stanza) {
    //TODO
  }

  public abstract Observable<Document> getIncomingStanzaStream();

  public abstract void connect() throws ConnectionEndpointNotFoundException;

  public abstract void disconnect();

  public abstract void fetchConnectionMethod()
      throws OperationNotReadyException, ConnectionEndpointNotFoundException;

  public State getState() {
    return state;
  }

  public String getDomain() {
    return domain;
  }

  public String getUsername() {
    return username;
  }

  public void setResource(String resource) {

  }

  public PluginManager getPluginManager() {
    return pluginManager;
  }

  public Logger getLogger() {
    return logger;
  }

  public @NonNull Disposable addEventHandler(@NonNull Class<? extends Event> event,
                                             @NonNull Consumer<Event> handler) {
    return eventStream.ofType(event).subscribe(handler);
  }

  public void login(@NonNull String password) {
    login(password, null);
  }

  public void login(@NonNull String password, @Nullable String resource) {

  }

  @Override
  public @NonNull Disposable addEventHandler(@NonNull Class<? extends Event> event,
                                             @NonNull Consumer<Event> handler,
                                             long timesOfHandling,
                                             @Nullable Scheduler scheduler) {
    return eventStream.ofType(event)
                      .take(timesOfHandling)
                      .observeOn(scheduler)
                      .subscribe(handler);
  }
}