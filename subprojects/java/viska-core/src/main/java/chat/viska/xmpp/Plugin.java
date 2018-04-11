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

import chat.viska.commons.XmlTagSignature;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.ThreadSafe;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Plugin for an {@link Session}.
 */
@ThreadSafe
public interface Plugin {

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
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a>
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
   *
   * <p>Dependencies can be declared using {@link DependsOn}. Keep in mind to avoid circular
   * dependencies in any way, since this will cause an infinite loop when applying the plugins.</p>
   */
  default Set<Class<? extends Plugin>> getAllDependencies() {
    final Set<Class<? extends Plugin>> dependencies = new LinkedHashSet<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable DependsOn annotation = clazz.getAnnotation(DependsOn.class);
      if (annotation != null) {
        dependencies.addAll(Arrays.asList(annotation.value()));
      }
      clazz = clazz.getSuperclass();
    }
    return dependencies;
  }

  /**
   * Gets the features enabled by this type and all its parent types. Results of this method
   * are served when another peer entity is querying service info on this XMPP
   * client.
   * @see <a href="https://xmpp.org/extensions/xep-0030.html">XEP-0030: Service Discovery</a>
   */
  default Set<String> getAllFeatures() {
    final Set<String> features = new LinkedHashSet<>();
    Class<?> clazz = getClass();
    while (clazz != null) {
      final @Nullable Features annotation = clazz.getAnnotation(Features.class);
      if (annotation != null) {
        features.addAll(Arrays.asList(annotation.value()));
      }
      clazz = clazz.getSuperclass();
    }
    return features;
  }

  /**
   * Gets the {@code <iq/>} sub-element types currently supported by the plugin.
   * The result of this method is used to register any interested {@code <iq/>} type for a
   * {@link Plugin} so that it will only receive {@code <iq/>} with such signature. If no plugin
   * handles a particular {@code <iq/>}, a stanza error will be sent.
   */
  Set<XmlTagSignature> getSupportedIqs();

  /**
   * Invoked when a {@link Plugin} is being applied to a {@link Session}.
   * @param context The {@link Session.PluginContext} that holds the {@link Plugin}.
   */
  void onApply(final Session.PluginContext context);
}