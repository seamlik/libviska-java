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

package chat.viska.xmpp.plugins.jingle;

import chat.viska.xmpp.CommonXmlns;
import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import chat.viska.xmpp.plugins.BasePlugin;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

@Plugin.DependsOn(BasePlugin.class)
@Plugin.Features(CommonXmlns.JINGLE)
public class JinglePlugin implements Plugin {

  private final Set<JingleSession> sessions = new HashSet<>();
  private Session.PluginContext context;

  public Set<JingleSession> getJingleSessions() {
    return Collections.unmodifiableSet(sessions);
  }

  @Override
  @Nonnull
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return Collections.singleton(
        new AbstractMap.SimpleImmutableEntry<>(CommonXmlns.JINGLE, "jingle")
    );
  }

  @Override
  public void onApplying(@Nonnull Session.PluginContext context) {
    this.context = context;
  }

  @Nonnull
  @Override
  public Session getSession() {
    return context.getSession();
  }
}