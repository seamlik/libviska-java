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

/**
 * Thrown to indicate that some part of a {@link Jid} is too long.
 * <p>
 *   According to the RFC, any part of a {@link Jid} must not excceed 1023 bytes
 *   after being encoded in UTF-8.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.1">RFC 7622</a>
 * @since 0.1
 */
public class JidTooLongException extends Exception {

  public JidTooLongException() {
    super();
  }

  public JidTooLongException(String msg) {
    super(msg);
  }

  public JidTooLongException(String s, Throwable throwable) {
    super(s, throwable);
  }

  public JidTooLongException(Throwable throwable) {
    super(throwable);
  }
}
