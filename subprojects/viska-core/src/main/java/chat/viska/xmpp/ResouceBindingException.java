/*
 * Copyright 2017 Kai-Chung Yan (殷啟聰)
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

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.Objects;

public class ResouceBindingException extends HandshakeException {

  public enum Condition {
    BAD_REQUEST,
    CONFLICT,
    NOT_ALLOWED,
    RESOURCE_CONSTRAINT;

    /**
     * Returns a constant of {@link Condition} based on the XML tag name of the
     * stream error.
     * @param name The XML tag name of the stream error.
     * @return {@code null} If the specified enum type has no constant with the
     *         specified name
     */
    @Nullable
    public static Condition of(@NonNull final String name) {
      try {
        return Enum.valueOf(
            Condition.class,
            name.replace('-', '_').toUpperCase()
        );
      } catch (Exception ex) {
        return null;
      }
    }
  }

  private final Condition condition;



  public ResouceBindingException(@NonNull final Condition condition) {
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public ResouceBindingException(@NonNull final Condition condition,
                                 final String message) {
    super(message);
    Objects.requireNonNull(condition);
    this.condition = condition;
  }

  public ResouceBindingException(@NonNull final Condition condition,
                                 final String message,
                                 final Throwable cause) {
    super(message, cause);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public ResouceBindingException(@NonNull final Condition condition,
                                 final Throwable cause) {
    super(cause);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  /**
   * Gets the condition.
   */
  @NonNull
  public Condition getCondition() {
    return condition;
  }
}