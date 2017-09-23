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

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

/**
 * Plugin for an {@link Session}.
 */
public interface Plugin extends SessionAware {

  /**
   * Gets the dependencies. When this plugin is being applied, all dependencies
   * will also be applied automatically as well.
   */
  @Nonnull
  Set<Class<? extends Plugin>> getDependencies();

  /**
   * Gets the features currently enabled by the plugin. Results of this method
   * are served when another peer entity is querying service info on this XMPP
   * client.
   *
   * <p>This method is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a></p>
   */
  @Nonnull
  Set<String> getFeatures();

  /**
   * Gets the {@code <iq/>} sub-element types currently supported by the plugin.
   * Results of this method are used to identify if an inbound {@code <iq/>} is
   * supported by this XMPP client. If no plugin handles a particular
   * {@code <iq/>}, a stream error will be sent.
   * @return {@link Set} of {@link java.util.Map.Entry}s whose keys are XML
   *         namespaces and values are {@code <iq/>} sub-element tag names.
   */
  @Nonnull
  Set<Map.Entry<String, String>> getSupportedIqs();

  void onApplied(@Nonnull final Session session);
}