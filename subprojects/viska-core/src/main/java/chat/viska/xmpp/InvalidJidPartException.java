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
 * Thrown to indicate one part of a JID is invalid.
 * <p>
 *   There are 3 parts of a {@link Jid}: local, domain and resource.
 * </p>
 * <p>
 *   The local part must be an instance of the
 *   <a href="https://tools.ietf.org/html/rfc7613#section-3.2">
 *   UsernameCaseMapped profile of the PRECIS IdentifierClass</a>. Additionally,
 *   the local part must not contain any characters belonging to
 *   {@link Jid#localpartExcludedChars}.
 * </p>
 * <p>
 *   The domain part is the required component of a {@link Jid}, therefore must
 *   not be empty. Additionally, the domain part must be either an IP address or
 *   a domain name.
 * </p>
 * <p>
 *   The resource part must be an instance of the
 *   <a href="https://tools.ietf.org/html/rfc7613#section-4.2">OpaqueString
 *   profile of the PRECIS FreeformClass</a>.
 * </p>
 * @see <a href="https://tools.ietf.org/html/rfc7622#section-3.3">RFC 7622</a>
 */
public class InvalidJidPartException extends Exception {

  public InvalidJidPartException() {
    super();
  }

  public InvalidJidPartException(String msg) {
    super(msg);
  }

  public InvalidJidPartException(Throwable throwable) {
    super(throwable);
  }

  public InvalidJidPartException(String s, Throwable throwable) {
    super(s, throwable);
  }
}