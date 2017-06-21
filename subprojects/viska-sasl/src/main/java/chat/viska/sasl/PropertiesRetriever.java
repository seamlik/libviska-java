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
 * Function that retrieves sensitive data during an
 * <a href="https://datatracker.ietf.org/doc/rfc4422">SASL</a> authentication.
 */
public interface PropertiesRetriever {

  /**
   * Retrieves a {@link Map} containing sensitive data. What data types and key
   * should be provided is defined by the mechanism.
   * @param authnId Authentication ID.
   * @param mechanism Name of the SASL Mechanism.
   * @throws Exception If any error occurred during the retrieval.
   */
  Map<String, ?> retrieve(String authnId, String mechanism) throws Exception;
}