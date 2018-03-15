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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.LinkedHashSet;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Plugin for an {@link Session}.
 */
@ThreadSafe
public interface Plugin extends SessionAware {

  /**
   * Specifies the dependencies of a {@link Plugin}.
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface DependsOn {
    Class<? extends Plugin>[] value();
  }

  /**
   * Specifies what features the plugin provides by default.
   *
   * <p>This API is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a></p>
   */
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @interface Features {
    String[] value();
  }

  /**
   * Gets all transitive and direct dependencies. When this plugin is being applied, all
   * dependencies will also be applied automatically.
   */
  default Set<Class<? extends Plugin>> getDependencies() {
    final Set<Class<? extends Plugin>> dependencies = new LinkedHashSet<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      if (clazz.isAnnotationPresent(DependsOn.class)) {
        dependencies.addAll(Arrays.asList(clazz.getAnnotation(DependsOn.class).value()));
      }
      clazz = clazz.getSuperclass();
    }
    return dependencies;
  }

  /**
   * Gets the features currently enabled by the plugin. Results of this method
   * are served when another peer entity is querying service info on this XMPP
   * client.
   *
   * <p>This API is part of
   * <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service
   * Discovery</a></p>
   */
  default Set<String> getFeatures() {
    final Set<String> features = new LinkedHashSet<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      if (clazz.isAnnotationPresent(Features.class)) {
        features.addAll(Arrays.asList(clazz.getAnnotation(Features.class).value()));
      }
      clazz = clazz.getSuperclass();
    }
    return features;
  }

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

  void onApplying(@Nonnull final Session.PluginContext context);
}