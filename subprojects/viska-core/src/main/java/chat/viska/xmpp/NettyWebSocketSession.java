/*
 * Copyright (C) 2017 Kai-Chung Yan (殷啟聰)
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

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.w3c.dom.Document;

/**
 * @since 0.1
 */
public class NettyWebSocketSession extends Session {

  @Override
  protected void sendOpeningStreamStanza() {

  }

  @Override
  protected void sendClosingStreamStanza() {

  }

  public NettyWebSocketSession(Jid loginJid) {
    super(loginJid);
  }

  @Override
  public @NonNull List<ConnectionMethod> queryConnectionMethod() {
    final List<ConnectionMethod> result = new ArrayList<>();
    try {
      for (ConnectionMethod it : ConnectionMethod.queryHostMetaJson(getLoginJid().getDomainpart(), getProxy())) {
        if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
          result.add(it.toSecure());
        }
      }
    } catch (Exception ex) {
      getLogger().log(
          Level.WARNING,
          "Failed to find any WebSocket endpoint from `host-meta.json`.",
          ex
      );
    }
    if (result.isEmpty()) {
      try {
        for (ConnectionMethod it : ConnectionMethod.queryHostMetaXml(getLoginJid().getDomainpart(), getProxy())) {
          if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
            result.add(it.toSecure());
          }
        }
      } catch (Exception ex) {
        getLogger().log(
            Level.WARNING,
            "Failed to find any WebSocket endpoint from `host-meta`.",
            ex
        );
      }
    }
    if (result.isEmpty()) {
      try {
        for (ConnectionMethod it : ConnectionMethod.queryDnsTxt(getLoginJid().getDomainpart(), getProxy())) {
          if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
            result.add(it.toSecure());
          }
        }
      } catch (Exception ex) {
        getLogger().log(
            Level.WARNING,
            "Failed to find any secure WebSocket endpoint from DNS TXT record.",
            ex
        );
      }
    }
    return result;
  }

  @Override
  public void connect() throws ConnectionMethodNotFoundException {
    super.connect();
    //TODO
  }

  @Override
  public void disconnect() {
    super.disconnect();
  }

  @Override
  public void send(Document stanza) {
    //TODO
  }

  @Override
  public Observable<Document> getIncomingStanzaStream() {
    return null;
  }
}