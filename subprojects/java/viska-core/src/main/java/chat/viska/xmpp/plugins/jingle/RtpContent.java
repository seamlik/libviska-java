/*
 * Copyright 2018 Kai-Chung Yan (殷啟聰)
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import rxbeans.MutableProperty;
import rxbeans.StandardProperty;

public abstract class RtpContent extends Content {

  public enum MediaType {
    AUDIO,
    VIDEO
  }

  public static class Description implements Content.Description {

    public static class PayloadType {}

    private final String name;
    private final List<PayloadType> payloadTypes;
    private final IceTransport transport;
    private final MediaType mediaType;

    public Description(final String name,
                       final MediaType mediaType,
                       final List<PayloadType> payloadTypes,
                       final IceTransport transport) {
      this.name = name;
      this.payloadTypes = Collections.unmodifiableList(new ArrayList<>(payloadTypes));
      this.transport = transport;
      this.mediaType = mediaType;
    }

    public IceTransport getTransport() {
      return transport;
    }

    public List<PayloadType> getPayloadTypes() {
      return payloadTypes;
    }

    public MediaType getMediaType() {
      return mediaType;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getNamespace() {
      return CommonXmlns.JINGLE_RTP;
    }
  }

  private final StandardProperty<Boolean> enabled = new StandardProperty<>(true);

  public abstract MediaType getMediaType();

  public MutableProperty<Boolean> enabledProperty() {
    return enabled;
  }
}
