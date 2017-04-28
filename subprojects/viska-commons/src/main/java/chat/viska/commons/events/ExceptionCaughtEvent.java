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

package chat.viska.commons.events;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.logging.Level;
import org.apache.commons.lang3.Validate;

public class ExceptionCaughtEvent extends Event {

  private Throwable cause;

  public ExceptionCaughtEvent(@NonNull EventSource source,
                              @NonNull Throwable cause,
                              @Nullable Level level) {
    super(source, level == null ? Level.WARNING : level, null);
    Validate.notNull(cause, "`cause` must not be null.");
    this.cause = cause;
  }

  public ExceptionCaughtEvent(@NonNull EventSource source, @NonNull Throwable cause) {
    this(source, cause, null);
  }

  public Throwable getCause() {
    return cause;
  }
}