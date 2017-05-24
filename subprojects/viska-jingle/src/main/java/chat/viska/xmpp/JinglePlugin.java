/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;
import java.util.AbstractMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JinglePlugin implements Plugin {

  private static final Set<Map.Entry<String, String>> SUPPORTED_STANZAS = new HashSet<>(1);
  private static final String XMLNS_JINGLE = "urn:xmpp:jingle:1";

  static {
    SUPPORTED_STANZAS.add(new AbstractMap.SimpleImmutableEntry<>(
        XMLNS_JINGLE,
        "jingle"
    ));
  }

  private Session session;
  private Set<JingleSession> jingleSessions = new HashSet<>();

  public JinglePlugin(final @NonNull Session session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    Set<Class<? extends Plugin>> dependencies = new HashSet<>(1);
    dependencies.add(BasePlugin.class);
    return dependencies;
  }

  @Override
  public Set<String> getFeatures() {
    Set<String> features = new HashSet<>();
    //features.add(JingleInfoQuery.Jingle.XMLNS);
    return features;
  }

  @Override
  @NonNull
  public Set<Map.Entry<String, String>> getSupportedStanzas() {
    return SUPPORTED_STANZAS;
  }

  @Override
  @NonNull
  public Session getSession() {
    return session;
  }
}