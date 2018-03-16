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
import javax.annotation.concurrent.NotThreadSafe;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> client.
 */
@NotThreadSafe
public interface Client {

  /**
   * Gets the name of the SASL mechanism.
   */
  String getMechanism();

  /**
   * Gets a response.
   * @return {@code null} if the authentication fails while generating the
   *         response, or a {@link String} containing the response which is
   *         either empty or not.
   * @throws IllegalStateException If still waiting for another challenge after
   *         sending the last response.
   */
  byte[] respond() throws AuthenticationException;

  /**
   * Accepts a challenge sent from the server.
   * @throws IllegalStateException If invoked before sending a response after
   *         accepting the last challenge.
   */
  void acceptChallenge(byte[] challenge) throws AuthenticationException;

  /**
   * Indicates if the mechanism requires the client to send an initial response.
   */
  boolean isClientFirst();

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
   * Gets a {@link Map} containing properties negotiated during the
   * authentication. What key-value pairs it will contain is defined by the
   * implementations.
   */
  Map<String, ?> getNegotiatedProperties();
}