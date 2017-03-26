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
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import java.net.Proxy;
import java.util.List;
import java.util.logging.Logger;
import org.apache.commons.lang3.Validate;
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

    public StateChangedEvent(@NonNull EventSource source, @NonNull State lastState) {
      super(source, null);
      this.lastState = lastState;
    }

    public State getLastState() {
      return lastState;
    }
  }

  private State state = State.DISCONNECTED;
  private Jid loginJid;
  private final PluginManager pluginManager = new PluginManager(this);
  private Subject<Event> eventStream = PublishSubject.create();
  private Logger logger;
  private Proxy proxy;
  private ConnectionMethod connectionMethod;

  protected Session(@NonNull Jid loginJid) {
    Validate.notNull(loginJid, "`loginJid` must not be null");
    this.loginJid = loginJid.toBareJid();
    logger = Logger.getLogger(
        "chat.viska.xmpp.Session_"+ loginJid.toString()
    );
  }

  protected abstract void sendOpeningStreamStanza();

  protected abstract void sendClosingStreamStanza();

  protected void triggerEvent(Event event) {
    eventStream.onNext(event);
  }

  private void setState(State state) {
    State lastState = this.state;
    this.state = state;
    triggerEvent(new StateChangedEvent(this, lastState));
  }

  public abstract void send(@NonNull Document stanza);

  public abstract Observable<Document> getIncomingStanzaStream();

  public abstract @NonNull List<ConnectionMethod> queryConnectionMethod();

  public void connect() throws ConnectionMethodNotFoundException {
    switch (state) {
      case CONNECTED:
        return;
      case CONNECTING:
        return;
      case DISCONNECTING:
        throw new IllegalStateException("Client is disconnecting.");
      case ONLINE:
        return;
      case OFFLINE:
        return;
      default:
        break;
    }
    setState(State.CONNECTING);
    if (connectionMethod == null) {
      connectionMethod = queryConnectionMethod().get(0);
    }
    if (connectionMethod == null) {
      throw new ConnectionMethodNotFoundException();
    }
    //TODO
  }

  public void disconnect() {
    switch (getState()) {
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
    if (getState() != State.DISCONNECTED) {
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

  public Logger getLogger() {
    return logger;
  }



  public void login(@NonNull String password) {
    login(password, null);
  }

  public void login(@NonNull String password, @Nullable String resource) {

  }

  @Override
  public @NonNull Disposable addEventHandler(@NonNull Class<? extends Event> event,
                                             @NonNull Consumer<Event> handler,
                                             long timesOfHandling) {
    if (timesOfHandling > 0L) {
      return eventStream.ofType(event).take(timesOfHandling).subscribe(handler);
    } else if (timesOfHandling == 0L) {
      return eventStream.ofType(event).subscribe(handler);
    } else {
      throw new IllegalArgumentException("`timesOfHandlng` must be non-negative.");
    }
  }

  @Override
  public @NonNull Disposable addEventHandler(@NonNull Class<? extends Event> event,
                                             @NonNull Consumer<Event> handler) {
    return eventStream.ofType(event).subscribe(handler);
  }

  @Override
  public @NonNull Disposable addEventHandlerOnce(@NonNull Class<? extends Event> event,
                                                 @NonNull Consumer<Event> handler) {
    return addEventHandler(event, handler, 1L);
  }
}