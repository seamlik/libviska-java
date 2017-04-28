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

import chat.viska.xmpp.BasePlugin;
import chat.viska.xmpp.JingleSession;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.stanzas.Stanza;
import java.util.HashSet;
import java.util.Set;

/**
 * @since 0.1
 */
public class JinglePlugin extends Plugin {

  private Set<? extends JingleSession> jingleSessions;


  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    Set<Class<? extends Plugin>> dependencies = new HashSet<>(1);
    dependencies.add(BasePlugin.class);
    return dependencies;
  }

  @Override
  public boolean quickMatch(Stanza stanza) {
    throw new RuntimeException();
  }

  @Override
  public Set<String> getFeatures() {
    Set<String> features = new HashSet<>();
    //features.add(JingleInfoQuery.Jingle.XMLNS);
    return features;
  }
}