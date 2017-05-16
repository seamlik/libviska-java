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

import io.reactivex.annotations.NonNull;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * XMPP AbstractPlugin.
 * @since 0.1
 */
public abstract class AbstractPlugin implements SessionAware {

  private AbstractSession session;

  @NonNull
  public abstract Set<Class<? extends AbstractPlugin>> getDependencies();

  @NonNull
  public abstract Set<String> getFeatures();

  @NonNull
  public abstract Set<Map.Entry<String, String>> getSupportedStanzas();

  protected AbstractPlugin(@NonNull AbstractSession session) {
    Objects.requireNonNull(session);
    this.session = session;
  }

  @Override
  @NonNull
  public AbstractSession getSession() {
    return session;
  }
}