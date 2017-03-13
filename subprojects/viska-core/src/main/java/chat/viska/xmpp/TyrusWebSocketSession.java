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

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.URL;
import java.util.logging.Level;
import org.apache.commons.lang3.Validate;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;

/**
 * @since 0.1
 */
public class TyrusWebSocketSession extends Session {

  private String serverEndpoint;
  private Proxy proxy;

  private static final String XMPP_WEBSOCKET_HOST_META_NAMESPACE =
      "urn:xmpp:alt-connections:websocket";

  /**
   * Find the WebSocket endpoint from a {@link host-meta.json}. If there are
   * multiple WebSocket endpoints listed, return the first one.
   * @throws ServerConnectionEndpointNotFoundException If failed to get the endpoint
   *         from {@code hostMeta} or {@code hostMeta} is malformed.
   * @throws NullPointerException If {@code hostMeta} is {@code null}.
   */
  private static @NonNull String findWebSocketEndpoint(@NonNull JsonElement hostMeta)
      throws ServerConnectionEndpointNotFoundException {
    Validate.notNull(hostMeta);
    try {
      return Observable.fromIterable(
          hostMeta.getAsJsonObject().getAsJsonArray("links")
      ).filter(element -> {
        return element.getAsJsonObject()
                      .getAsJsonPrimitive("rel")
                      .getAsString()
                      .equals(XMPP_WEBSOCKET_HOST_META_NAMESPACE);
      }).firstElement()
          .blockingGet(JsonNull.INSTANCE)
          .getAsJsonObject()
          .getAsJsonPrimitive("href")
          .getAsString();
    } catch (Exception ex) {
      throw new ServerConnectionEndpointNotFoundException(ex);
    }
  }

  /**
   * Find the WebSocket endpoint from a {@code host-meta} XML document. If there
   * are multiple WebSocket endpoints listed, return the first one.
   * @throws ServerConnectionEndpointNotFoundException If failed to get the endpoint
   *         from {@code hostMeta} or {@code hostMeta} is malformed.
   * @throws NullPointerException If {@code hostMeta} is {@code null}.
   */
  private static @NonNull String findWebSocketEndpoint(Document hostMeta)
      throws ServerConnectionEndpointNotFoundException {
    Validate.notNull(hostMeta);
    String result = null;
    try {
      return Observable.fromIterable(
          hostMeta.getRootElement().elements("Link")
      ).filter(element -> {
        return element.attributeValue("rel").equals(XMPP_WEBSOCKET_HOST_META_NAMESPACE);
      }).firstElement().blockingGet().attributeValue("href");
    } catch (Exception ex) {
      throw new ServerConnectionEndpointNotFoundException(ex);
    }
  }

  private static @NonNull JsonElement getHostMetaJson(@NonNull String host,
                                                      @Nullable Proxy proxy)
      throws HostMetaNotFoundException {
    try {
      final URL hostMetaUrl = new URL(
          "https://" + host + "/.well-known/host-meta.json"
      );
      return new JsonParser().parse(
          proxy == null ? new InputStreamReader(hostMetaUrl.openStream())
                        : new InputStreamReader(hostMetaUrl.openConnection(proxy)
                                                           .getInputStream())
      );
    } catch (Exception ex) {
      throw new HostMetaNotFoundException(ex);
    }
  }

  private static @NonNull Document getHostMetaXml(@NonNull String host,
                                                  @Nullable Proxy proxy)
      throws HostMetaNotFoundException {
    try {
      final URL hostMetaUrl = new URL(
          "https://" + host + "/.well-known/host-meta.json"
      );
      return new SAXReader().read(
          proxy == null ? new InputStreamReader(hostMetaUrl.openStream())
                        : new InputStreamReader(hostMetaUrl.openConnection(proxy)
                                                           .getInputStream())
      );
    } catch (Exception ex) {
      throw new HostMetaNotFoundException(ex);
    }
  }

  public TyrusWebSocketSession(String server, String username) {
    super(server, username);
  }

  @Override
  public void connect(String host, Proxy proxy) throws ServerConnectionEndpointNotFoundException {
    setState(State.CONNECTING);
    if (this.proxy == null) {
      this.proxy = proxy;
    }
    if (host != null) {
      this.serverEndpoint = host;
    }
    if (this.serverEndpoint == null) {
      try {
        this.serverEndpoint = findWebSocketEndpoint(getHostMetaJson(host, proxy));
      } catch (Exception ex) {
        getLogger().log(
            Level.WARNING,
            "Failed to determine the WebSocket endpoint of `" + host + "`.",
            ex
        );
      }
      if (this.serverEndpoint == null) {
        try {
          this.serverEndpoint = findWebSocketEndpoint(getHostMetaXml(host, proxy));
        } catch (Exception ex) {
          getLogger().log(
              Level.WARNING,
              "Failed to determine the WebSocket endpoint of `" + host + "`.",
              ex
          );
        }
      }
    }
  }

  @Override
  public void disconnect() {
    setState(State.DISCONNECTING);
  }

  @Override
  public void send(Document stanza) {

  }

  @Override
  public Observable<Document> getIncomingStanzas() {
    return null;
  }
}