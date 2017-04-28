/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.xmpp;

import chat.viska.commons.events.Event;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggingManager implements SessionAware {

  private Level loggingLevel = Level.WARNING;
  private final Logger logger;
  private final Session session;

  public LoggingManager(Session session) {
    this.session = session;
    logger = Logger.getLogger(
        "chat.viska.xmpp.Session_"+ session.getLoginJid().toString()
    );
    session.getEventStream().subscribe(new Consumer<Event>() {
      @Override
      public void accept(Event event) throws Exception {
        log(event, null);
      }
    });
  }

  public Level getLoggingLevel() {
    return loggingLevel;
  }

  public void setLoggingLevel(Level loggingLevel) {
    this.loggingLevel = loggingLevel;
  }

  public Logger getLogger() {
    return logger;
  }

  public void log(@NonNull Throwable throwable, @NonNull Level level, @Nullable String msg) {
    //TODO
  }

  public void log(@NonNull Event event, @Nullable String msg) {
    //TODO
  }

  @Override
  public Session getSession() {
    return session;
  }
}