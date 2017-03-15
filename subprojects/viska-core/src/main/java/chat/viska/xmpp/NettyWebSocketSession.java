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

import chat.viska.OperationNotReadyException;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.logging.Level;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * @since 0.1
 */
public class NettyWebSocketSession extends Session {

  private String connectionEndpoint;
  private Proxy proxy;
  private Integer port;

  private static final String XMPP_WEBSOCKET_HOST_META_NAMESPACE =
      "urn:xmpp:alt-connections:websocket";

  /**
   * Find the WebSocket endpoint from a {@link host-meta.json}. If there are
   * multiple WebSocket endpoints listed, return the first one.
   * @throws ConnectionEndpointNotFoundException If failed to get the endpoint
   *         from {@code hostMeta} or {@code hostMeta} is malformed.
   * @throws NullPointerException If {@code hostMeta} is {@code null}.
   */
  private static @NonNull String findWebSocketEndpoint(@NonNull JsonElement hostMeta)
      throws ConnectionEndpointNotFoundException {
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
      throw new ConnectionEndpointNotFoundException(ex);
    }
  }

  /**
   * Find the WebSocket endpoint from a {@code host-meta} XML document. If there
   * are multiple WebSocket endpoints listed, return the first one.
   * @throws ConnectionEndpointNotFoundException If failed to get the endpoint
   *         from {@code hostMeta} or {@code hostMeta} is malformed.
   * @throws NullPointerException If {@code hostMeta} is {@code null}.
   */
  private static @NonNull String findWebSocketEndpoint(Document hostMeta)
      throws ConnectionEndpointNotFoundException {
    Validate.notNull(hostMeta);
    try {
      NodeList links = hostMeta.getDocumentElement()
                               .getElementsByTagName("Link");
      return Observable.range(
          0, links.getLength()
      ).map(
          index -> (Element) links.item(index)
      ).filter(element -> {
        return element.getAttribute("rel").equals(XMPP_WEBSOCKET_HOST_META_NAMESPACE);
      }).firstElement().blockingGet().getAttribute("href");
    } catch (Exception ex) {
      throw new ConnectionEndpointNotFoundException(ex);
    }
  }

  /**
   * Returns a URL to the host-meta file.
   * <p>This method forces to use HTTPS URL for security reasons.</p>
   * @param host The host name of the server, might be an IP address, a domain
   *             name, {@code localhost}, etc.. If it specifies a protocol other
   *             than HTTPS, the protocol is still changed to HTTPS.
   * @param json Indicates whether to get the JSON version or the XML version.
   * @return
   * @throws MalformedURLException
   */
  private static @NonNull URL getHostMetaUrl(@NonNull String host, boolean json)
      throws MalformedURLException {
    final String hostTrimmed = host.trim();
    final String hostMeta = "/.well-known/host-meta" + (json ? ".json" : "");
    return new URL(
        "https",
        host.trim(),
        hostTrimmed.endsWith("/") ? hostMeta.substring(1) : hostMeta
    );
  }

  private static @NonNull JsonElement getHostMetaJson(@NonNull String host,
                                                      @Nullable Proxy proxy)
      throws HostMetaNotFoundException, IOException {
    InputStreamReader reader = null;
    InputStream stream = null;
    JsonElement hostMeta = null;
    try {
      final URL hostMetaUrl = getHostMetaUrl(host, true);
      stream = proxy == null ? hostMetaUrl.openStream()
                             : hostMetaUrl.openConnection(proxy).getInputStream();
      reader = new InputStreamReader(stream);
      hostMeta = new JsonParser().parse(reader);
    } catch (Exception ex) {
      throw new HostMetaNotFoundException(ex);
    } finally {
      if (stream != null) {
        stream.close();
      }
      if (reader != null) {
        reader.close();
      }
    }
    if (hostMeta == null || !hostMeta.isJsonObject()) {
      throw new HostMetaNotFoundException("Malformed `host-meta.json`.");
    }
    return hostMeta;
  }

  private static @NonNull Document getHostMetaXml(@NonNull String host,
                                                  @Nullable Proxy proxy)
      throws HostMetaNotFoundException, IOException {
    InputStreamReader reader = null;
    InputStream stream = null;
    Document hostMeta = null;
    try {
      final URL hostMetaUrl = getHostMetaUrl(host, false);
      stream = proxy == null ? hostMetaUrl.openStream()
                             : hostMetaUrl.openConnection(proxy).getInputStream();
      reader = new InputStreamReader(stream);
      hostMeta = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
      hostMeta.normalizeDocument();
    } catch (Exception ex) {
      throw new HostMetaNotFoundException(ex);
    } finally {
      if (stream != null) {
        stream.close();
      }
      if (reader != null) {
        reader.close();
      }
    }
    if (hostMeta == null) {
      throw new HostMetaNotFoundException("Malformed `host-meta.json`.");
    }
    return hostMeta;
  }

  public NettyWebSocketSession(String server, String username) {
    super(server, username);
  }

  @Override
  public void fetchConnectionMethod()
      throws OperationNotReadyException, ConnectionEndpointNotFoundException {
    if (getState() != State.DISCONNECTED) {
      throw new OperationNotReadyException();
    }
    try {
      this.connectionEndpoint = findWebSocketEndpoint(getHostMetaJson(getDomain(), this.proxy));
    } catch (Exception ex) {
      getLogger().log(
          Level.WARNING,
          "Failed to determine the WebSocket endpoint from `host-meta.json` of `" + getDomain() + "`.",
          ex
      );
    }
    if (this.connectionEndpoint == null) {
      try {
        this.connectionEndpoint = findWebSocketEndpoint(getHostMetaXml(getDomain(), this.proxy));
      } catch (Exception ex) {
        getLogger().log(
            Level.WARNING,
            "Failed to determine the WebSocket endpoint from `host-meta` of `" + getDomain() + "`.",
            ex
        );
      }
    }
    if (this.connectionEndpoint == null) {
      throw new ConnectionEndpointNotFoundException(
          "Failed to determine the WebSocket endpoint of `" + getDomain() + "`."
      );
    }
  }

  @Override
  public void connect() throws ConnectionEndpointNotFoundException {
    switch (getState()) {
      case CONNECTED:
        return;
      case CONNECTING:
        return;
      case ONLINE:
        return;
      case OFFLINE:
        return;
      case DISCONNECTING:
        throw new OperationNotReadyException("Client is disconnecting.");
    }
    setState(State.CONNECTING);
    if (connectionEndpoint == null || port == null) {
      fetchConnectionMethod();
    }

  }

  @Override
  public void disconnect() {
    switch (getState()) {
      case DISCONNECTED:
        return;
      case DISCONNECTING:
        return;
      default:
        //TODO
    }
  }

  @Override
  public void send(String stanza) {

  }

  @Override
  public Observable<Document> getIncomingStanzaStream() {
    return null;
  }

  @Override
  public @Nullable Proxy getProxy() {
    return proxy;
  }

  @Override
  public void setProxy(@Nullable Proxy proxy) throws OperationNotReadyException {
    if (getState() != State.DISCONNECTED) {
      throw new OperationNotReadyException();
    }
    this.proxy = proxy;
  }

  @Override
  public @Nullable String getConnectionEndpoint() {
    return connectionEndpoint;
  }

  @Override
  public void setConnectionEndpoint(@NonNull String connectionEndpoint)
      throws OperationNotReadyException {
    Validate.notNull(connectionEndpoint);
    if (getState() != State.DISCONNECTED) {
      throw new OperationNotReadyException();
    }
    this.connectionEndpoint = connectionEndpoint;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void setPort(int port) throws OperationNotReadyException {
    if (getState() != State.DISCONNECTED) {
      throw new OperationNotReadyException();
    }
    this.port = port;
  }
}