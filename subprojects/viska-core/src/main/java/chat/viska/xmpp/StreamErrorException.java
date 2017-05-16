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
import java.util.Objects;
import org.apache.commons.lang3.Validate;

public class StreamErrorException extends Exception {

  public enum Condition {
    BAD_FORMAT,
    BAD_NAMESPACE_PREFIX,
    CONFLICT,
    CONNECTION_TIMEOUT,
    HOST_GONE,
    HOST_UNKNOWN,
    IMPROPER_ADDRESSING,
    INTERNAL_SERVER_ERROR,
    INVALID_FROM,
    INVALID_NAMESPACE,
    INVALID_XML,
    NOT_AUTHORIZED,
    NOT_WELL_FORMED,
    POLICY_VIOLATION,
    REMOTE_CONNECTION_FAILED,
    RESET,
    RESOURCE_CONSTRAINT,
    RESTRICTED_XML,
    SEE_OTHER_HOST,
    SYSTEM_SHUTDOWN,
    UNDEFINED_CONDITION,
    UNSUPPORTED_ENCODING,
    UNSUPPORTED_FEATURE,
    UNSUPPORTED_STANZA_TYPE,
    UNSUPPORTED_VERSION;

    @NonNull
    public Condition of(final @NonNull String value) {
      Validate.notBlank(value);
      return Enum.valueOf(
          Condition.class,
          value.replace('-', '_').toUpperCase()
      );
    }

    @Override
    public String toString() {
      return name().replace('_', '-').toLowerCase();
    }
  }

  private Condition condition;

  public StreamErrorException(final @NonNull Condition condition) {
    super(condition.toString());
    Objects.requireNonNull(condition);
    this.condition = condition;
  }

  public StreamErrorException(final @NonNull Condition condition,
                              final String s,
                              final Throwable throwable) {
    super(s, throwable);
    Objects.requireNonNull(condition);
    this.condition = condition;
  }

  public StreamErrorException(final @NonNull Condition condition,
                              final Throwable throwable) {
    super(condition.toString(), throwable);
    Objects.requireNonNull(condition);
    this.condition = condition;
  }
}