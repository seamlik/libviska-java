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

import java.nio.charset.Charset;

/**
 * SASL client.
 */
public interface Client {

  /**
   * Gets the name of the SASL mechanism.
   */
  String getMechanism();

  /**
   * Gets a response.
   * @throws IllegalStateException If still waiting for another challenge after
   *         sending the last response.
   */
  String respond();

  /**
   * Accept an challenge sent from the server.
   * @throws IllegalStateException If invoked before sending a response after
   *         accepting the last challenge.
   */
  void acceptChallenge(String challenge);

  /**
   * Indicates if the mechanism requires the client to send an initial response.
   */
  boolean isClientFirst();

  /**
   * Indicates if the authentication is finished and successful or is still in
   * progress.
   * @throws AuthenticationException If the authentication failed.
   */
  boolean isCompleted() throws AuthenticationException;

  /**
   * Gets the {@link Charset} used in transferring responses and challenges.
   */
  Charset getCharset();
}