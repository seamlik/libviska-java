/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
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

package chat.viska;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.logging.Level;
import org.apache.commons.lang3.Validate;
import org.joda.time.Instant;

public abstract class Event {

  private EventSource source;
  private Instant triggeredTime;
  private String message;
  private Level level;

  public Event(@NonNull EventSource source, @Nullable Level level, @Nullable String message) {
    Validate.notNull(source, "`source` must not be null");
    this.source = source;
    this.level = level == null ? Level.FINE : level;
    this.triggeredTime = Instant.now();
    this.message = message == null ? "" : message;
  }

  public @NonNull EventSource getSource() {
    return source;
  }

  public @NonNull Instant getTriggeredTime() {
    return triggeredTime;
  }

  public @NonNull String getMessage() {
    return message;
  }

  public @NonNull Level getLevel() {
    return level;
  }
}