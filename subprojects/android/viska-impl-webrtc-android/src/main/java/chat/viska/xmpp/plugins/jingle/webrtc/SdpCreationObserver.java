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

package chat.viska.xmpp.plugins.jingle.webrtc;

import io.reactivex.subjects.SingleSubject;
import org.webrtc.SessionDescription;

public class SdpCreationObserver implements org.webrtc.SdpObserver {

  private final SingleSubject<SessionDescription> result;

  public SdpCreationObserver(final SingleSubject<SessionDescription> result) {
    this.result = result;
  }

  @Override
  public void onCreateSuccess(final SessionDescription sdp) {
    result.onSuccess(sdp);
  }

  @Override
  public void onSetSuccess() { }

  @Override
  public void onCreateFailure(final String msg) {
    result.onError(new Exception(msg));
  }

  @Override
  public void onSetFailure(final String msg) { }
}
