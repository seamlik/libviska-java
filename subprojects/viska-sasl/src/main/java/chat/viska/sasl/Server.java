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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> server.
 */
@NotThreadSafe
public interface Server {

  /**
   * Gets the name of the SASL mechanism.
   */
  @Nonnull
  String getMechanism();

  /**
   * Generates a challenge.
   * @return {@code null} if error occurred.
   */
  @Nullable
  byte[] challenge();

  /**
   * Accepts and processes a response.
   */
  void acceptResponse(byte[] response);

  /**
   * Indicates if the mechanism requires the server to send an initial challenge.
   */
  boolean isServerFirst();

  /**
   * Indicates if the authentication is finished and successful or is still in
   * progress.
   */
  boolean isCompleted();

  /**
   * Gets the error occurred during the authentication.
   * @throws IllegalStateException If authentication not completed.
   */
  @Nullable
  AuthenticationException getError();

  /**
   * Gets the authorization ID in effect for the client of this session. If the
   * client did not specify one, it returns the authentication ID instead.
   * @throws IllegalStateException If authentication not completed.
   */
  @Nonnull
  String getAuthorizationId();

  /**
   * Gets a {@link Map} containing properties negotiated during the
   * authentication. What key-value pairs it will contain is defined by the
   * implementations.
   */
  @Nonnull
  Map<String, ?> getNegotiatedProperties();
}