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

package chat.viska.xmpp;

import io.reactivex.annotations.NonNull;

public enum StreamFeature {

  RESOURCE_BINDING(CommonXmlns.RESOURCE_BINDING, "bind", false, true),
  RESOURCE_BINDING_2(CommonXmlns.RESOURCE_BINDING_2, "bind", false, true),
  ROSTER_VERSION(CommonXmlns.ROSTER_VERSION, "ver", false, false),
  SASL(CommonXmlns.SASL, "mechanisms", true, true),
  STARTTLS(CommonXmlns.STARTTLS, "starttls", true, false),
  STREAM_COMPRESSION(CommonXmlns.STREAM_COMPRESSION, "compression", false, false),
  STREAM_MANAGEMENT(CommonXmlns.STREAM_MANAGEMENT, "SM", false, false);

  private final String name;
  private final String namespace;
  private final boolean restartRequired;
  private final boolean mandatory;

  StreamFeature(@NonNull final String namespace, @NonNull final String name,
                final boolean restartRequired,
                final boolean mandatory) {
    this.name = name;
    this.namespace = namespace;
    this.restartRequired = restartRequired;
    this.mandatory = mandatory;
  }

  public String getName() {
    return name;
  }

  public String getNamespace() {
    return namespace;
  }

  public boolean isRestartRequired() {
    return restartRequired;
  }

  public boolean isMandatory() {
    return mandatory;
  }
}