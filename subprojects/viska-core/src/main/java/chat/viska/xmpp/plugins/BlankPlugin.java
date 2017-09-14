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

import chat.viska.xmpp.Plugin;
import chat.viska.xmpp.Session;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public abstract class BlankPlugin implements Plugin {

  private Session session;

  protected BlankPlugin() {}

  @Override
  public void onApplied(Session session) {
    this.session = session;
  }

  @Override
  public Session getSession() {
    return session;
  }

  @Override
  public void onSessionDisconnected() {}

  @Override
  public void onSessionDisposed() {}

  @Override
  public void onSessionOnline() {}

  @Override
  public Set<Class<? extends Plugin>> getDependencies() {
    return Collections.emptySet();
  }

  @Override
  public Set<Map.Entry<String, String>> getSupportedIqs() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getFeatures() {
    return Collections.emptySet();
  }
}