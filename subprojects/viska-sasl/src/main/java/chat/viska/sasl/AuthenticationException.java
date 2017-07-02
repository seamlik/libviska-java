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

package chat.viska.sasl;

import java.util.Objects;

/**
 * Error occurred during an
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> authentication.
 */
public class AuthenticationException extends Exception {

  public enum Condition {
    ABORTED,
    ACCOUNT_DISABLED,
    CLIENT_NOT_AUTHORIZED,
    CREDENTIALS_EXPIRED,
    CREDENTIALS_NOT_FOUND,
    ENCRYPTION_REQUIRED,
    INCORRECT_ENCODING,
    INVALID_AUTHZID,
    INVALID_MECHANISM,
    MALFORMED_REQUEST,
    MECHANISM_TOO_WEEK,
    SERVER_NOT_AUTHORIZED,
    TEPORARY_AUTH_FAILURE
  }

  private final Condition condition;

  public AuthenticationException(Condition condition) {
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public AuthenticationException(Condition condition, String text) {
    super(text);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public AuthenticationException(Condition condition,
                                 String text,
                                 Throwable throwable) {
    super(text, throwable);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public AuthenticationException(Condition condition, Throwable throwable) {
    super(throwable);
    Objects.requireNonNull(condition, "`condition` is absent.");
    this.condition = condition;
  }

  public Condition getCondition() {
    return condition;
  }
}