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
import java.util.logging.Logger;
import org.dom4j.Document;

/**
 * @since 0.1
 */
public abstract class Session implements EventSource {

  public enum State {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    DISCONNECTING
  }

  public static class StateChangedEvent extends Event {
    private EventSource source;
    private State lastState;

    public StateChangedEvent(EventSource source, State lastState) {
      this.source = source;
      this.lastState = lastState;
    }

    @Override
    public EventSource getSource() {
      return source;
    }

    public State getLastState() {
      return lastState;
    }
  }

  private State state = State.DISCONNECTED;
  private String server;
  private String username;
  private final PluginManager pluginManager = new PluginManager(this);
  private Subject<Event> eventStream = PublishSubject.create();
  private Logger logger;

  protected Session(@NonNull String server, @Nullable String username) {
    this.server = server;
    this.username = username;
    logger = Logger.getLogger(
        "chat.viska.xmpp.Session_"+ username + "@" + server
    );
  }

  protected void fireEvent(Event event) {
    eventStream.onNext(event);
  }

  protected void setState(State state) {
    State lastState = this.state;
    this.state = state;
    eventStream.onNext(new StateChangedEvent(this, lastState));
  }

  public abstract void send(Document stanza);

  public abstract Observable<Document> getIncomingStanzas();

  public abstract void connect(@Nullable String host,
                               @Nullable Proxy proxy)
      throws ServerConnectionEndpointNotFoundException;

  public abstract void disconnect();


  public State getState() {
    return state;
  }

  public String getServer() {
    return server;
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

  @Override
  public Observable<Event> getEventStream() {
    return eventStream;
  }
}