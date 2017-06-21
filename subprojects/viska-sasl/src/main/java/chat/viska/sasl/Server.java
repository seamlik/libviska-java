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

import java.util.Map;

/**
 * SASL server.
 */
public interface Server {

  /**
   * Gets the name of the SASL mechanism.
   */
  String getMechanism();

  byte[] challenge();

  void acceptResponse(byte[] response);

  /**
   * Indicates if the mechanism requires the server to send an initial challenge.
   */
  boolean isServerFirst();

  /**
   * Indicates if the authentication is finished and successful or is still in
   * progress.
   * @throws AuthenticationException If the authentication failed.
   */
  boolean isCompleted() throws AuthenticationException;

  /**
   * Gets the authorization ID in effect for the client of this session. If the
   * client did not specify one, it returns the authentication ID instead.
   * @return {@code null} if authentication not completed.
   */
  String getAuthorizationId();

  /**
   * Gets a {@link Map} containing properties negotiated during the
   * authentication. What key-value pairs it will contain is defined by the
   * implementations.
   * @return {@code null} if authentication not completed, otherwise a
   *         {@link Map} which is either empty or containing properties.
   */
  Map<String, ?> getNegotiatedProperties();
}