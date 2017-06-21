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

import java.util.ResourceBundle;

/**
 * Error occurred during a
 * <a href="https://datatracker.ietf.org/doc/rfc5802">SCRAM</a> authentication.
 */
public class ScramException extends AuthenticationException {

  public ScramException() {
  }

  public ScramException(String s) {
    super(s);
  }

  public ScramException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public ScramException(Throwable throwable) {
    super(throwable);
  }

  @Override
  public String getLocalizedMessage() {
    final String msg;
    try {
      msg = ResourceBundle
          .getBundle(getClass().getSimpleName())
          .getString(getMessage());
    } catch (Exception ex) {
      return getMessage();
    }
    return msg;
  }
}