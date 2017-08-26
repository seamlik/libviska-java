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

package chat.viska.xmpp.plugins;

import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import io.reactivex.annotations.NonNull;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class RosterPlugin implements Plugin {

  private static final Set<Map.Entry<String, String>> supportedIqs = Collections.singleton(
      new AbstractMap.SimpleImmutableEntry<>(CommonXmlns.ROSTER, "query")
  );

  private final Session session;
  private final Roster roster;

  RosterPlugin(@NonNull final Session session) {
    this.session = session;
    this.roster = new Roster(session);
  }

  @NonNull
  public Roster getRoster() {
    return roster;
  }

  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getFeatures() {
    return Collections.emptySet();
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return supportedIqs;
  }

  @Override
  public Session getSession() {
    return session;
  }
}