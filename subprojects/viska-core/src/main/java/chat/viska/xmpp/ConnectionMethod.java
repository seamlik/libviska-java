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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ConnectionMethod {

  public enum Protocol {
    BOSH,
    TCP,
    WEBSOCKET
  }

  private Protocol protocol;
  private String scheme;
  private String host;
  private String path;
  private int port;

  public static final int DEFAULT_TCP_PORT_XMPP = 5222;
  public static final String HOST_META_NAMESPACE_BOSH = "urn:xmpp:alt-connections:xbosh";
  public static final String HOST_META_NAMESPACE_WEBSOCKET = "urn:xmpp:alt-connections:websocket";

  /**
   * Returns a URL to the host-meta file.
   * <p>This method forces to use HTTPS URL for security reasons.</p>
   * @param host The domain of the server, might be an IP address, a domain
   *               name, {@code localhost}, etc.. If it specifies a protocol
   *               other than HTTPS, HTTPS will still be used.
   * @param json Indicates whether to get the JSON version or the XML version.
   * @throws MalformedURLException If {@code domain} is invalid.
   */
  private static @NonNull URL getHostMetaUrl(@NonNull String host, boolean json)
      throws MalformedURLException {
    final String hostMetaPath = "/.well-known/host-meta" + (json ? ".json" : "");
    return new URL(
        "https",
        host,
        host.endsWith("/") ? hostMetaPath.substring(1) : hostMetaPath
    );
  }

  private static @NonNull JsonObject downloadHostMetaJson(@NonNull String host,
                                                          @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notNull(host, "`domain` must not be null.");
    InputStreamReader reader = null;
    InputStream stream = null;
    JsonObject hostMeta = null;
    try {
      final URL hostMetaUrl = getHostMetaUrl(host, true);
      stream = proxy == null ? hostMetaUrl.openStream()
                             : hostMetaUrl.openConnection(proxy).getInputStream();
      reader = new InputStreamReader(stream);
      hostMeta = new JsonParser().parse(reader).getAsJsonObject();
    } catch (Exception ex) {
      throw new InvalidHostMetaException(ex);
    } finally {
      if (stream != null) {
        stream.close();
      }
      if (reader != null) {
        reader.close();
      }
    }
    return hostMeta;
  }

  private static @NonNull Document downloadHostMetaXml(@NonNull String domain,
                                                       @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notNull(domain, "`domain` must not be null.");
    InputStreamReader reader = null;
    InputStream stream = null;
    Document hostMeta = null;
    try {
      final URL hostMetaUrl = getHostMetaUrl(domain, false);
      stream = proxy == null ? hostMetaUrl.openStream()
                             : hostMetaUrl.openConnection(proxy).getInputStream();
      reader = new InputStreamReader(stream);
      hostMeta = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
      hostMeta.normalizeDocument();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    } catch (Exception ex) {
      throw new InvalidHostMetaException(ex);
    } finally {
      if (stream != null) {
        stream.close();
      }
      if (reader != null) {
        reader.close();
      }
    }
    return hostMeta;
  }

  private static List<ConnectionMethod> queryHostMetaXml(@NonNull Document hostMeta)
      throws InvalidHostMetaException, IOException {
    Validate.notNull(hostMeta, "`hostMeta` must not be null.");
    final NodeList linkNodes = hostMeta.getDocumentElement().getElementsByTagName("Link");
    return Observable.range(
        0,
        linkNodes.getLength()
    ).map(new Function<Integer, Element>() {
      @Override
      public Element apply(Integer index) throws Exception {
        return (Element) linkNodes.item(index);
      }
    }).filter(new Predicate<Element>() {
      @Override
      public boolean test(Element element) throws Exception {
        final String rel = element.getAttribute("rel");
        return rel.equals(HOST_META_NAMESPACE_BOSH) || rel.equals(HOST_META_NAMESPACE_WEBSOCKET);
      }
    }).map(new Function<Element, ConnectionMethod>() {
      @Override
      public ConnectionMethod apply(Element element) throws Exception {
        Protocol protocol = null;
        switch (element.getAttribute("rel")) {
          case HOST_META_NAMESPACE_BOSH:
            protocol = Protocol.BOSH;
            break;
          case HOST_META_NAMESPACE_WEBSOCKET:
            protocol = Protocol.WEBSOCKET;
            break;
        }
        return new ConnectionMethod(protocol, new URI(element.getAttribute("href")));
      }
    }).toList().blockingGet();
  }

  public static List<ConnectionMethod> queryHostMetaXml(@NonNull String domain,
                                                        @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notBlank(domain, "`domain` must not be blank.");
    return queryHostMetaXml(downloadHostMetaXml(domain, proxy));
  }

  public static List<ConnectionMethod> queryHostMetaXml(@NonNull InputStream hostMeta)
      throws IOException, InvalidHostMetaException {
    Validate.notNull(hostMeta, "`hostMeta` must not be null.");
    Document hostMetaDocument = null;
    try {
      hostMetaDocument = DocumentBuilderFactory.newInstance()
                                               .newDocumentBuilder()
                                               .parse(hostMeta);
      hostMetaDocument.normalizeDocument();
    } catch (ParserConfigurationException ex) {
      throw new RuntimeException(ex);
    } catch (Exception ex) {
      throw new InvalidHostMetaException(ex);
    }
    return queryHostMetaXml(hostMetaDocument);
  }

  private static List<ConnectionMethod> queryHostMetaJson(@NonNull JsonObject hostMeta) {
    Validate.notNull(hostMeta, "`hostMeta` must not be null.");
    return Observable.fromIterable(
        hostMeta.getAsJsonObject().getAsJsonArray("links")
    ).filter(new Predicate<JsonElement>() {
      @Override
      public boolean test(JsonElement element) throws Exception {
        final String rel = element.getAsJsonObject()
                                  .getAsJsonPrimitive("rel")
                                  .getAsString();
        return rel.equals(HOST_META_NAMESPACE_BOSH) || rel.equals(HOST_META_NAMESPACE_WEBSOCKET);
      }
    }).map(new Function<JsonElement, ConnectionMethod>() {
      @Override
      public ConnectionMethod apply(JsonElement element) throws Exception {
        Protocol protocol = null;
        final String rel = element.getAsJsonObject()
                                  .getAsJsonPrimitive("rel")
                                  .getAsString();
        switch (rel) {
          case HOST_META_NAMESPACE_BOSH:
            protocol = Protocol.BOSH;
            break;
          case HOST_META_NAMESPACE_WEBSOCKET:
            protocol = Protocol.WEBSOCKET;
            break;
        }
        return new ConnectionMethod(
            protocol,
            new URI(
                element.getAsJsonObject()
                       .getAsJsonPrimitive("href")
                       .getAsString()
            )
        );
      }
    }).toList().blockingGet();
  }

  public static List<ConnectionMethod> queryHostMetaJson(@NonNull Reader hostMeta) {
    return queryHostMetaJson(new JsonParser().parse(hostMeta).getAsJsonObject());
  }

  public static List<ConnectionMethod> queryHostMetaJson(@NonNull String domain,
                                                         @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notBlank(domain, "`domain` must not be blank.");
    return queryHostMetaJson(downloadHostMetaJson(domain, proxy));
  }

  public static List<ConnectionMethod> queryDnsTxt(@NonNull String domain,
                                                   @Nullable Proxy proxy) {
    Validate.notNull(domain, "`domain` must not be null.");
    throw new RuntimeException();
  }

  public ConnectionMethod(@NonNull Protocol protocol,
                          @Nullable String scheme,
                          @NonNull String host,
                          int port,
                          @Nullable String path) {
    Validate.notNull(protocol, "`protocol` must not be null.");
    Validate.notNull(host, "`host` must not be null.");
    this.protocol = protocol;
    this.scheme = scheme == null ? "" : scheme;
    this.host = host;
    this.port = port;
    this.path = path == null ? "" : path;
  }

  public ConnectionMethod(@NonNull Protocol protocol, @NonNull URI uri) {
    Validate.notNull(protocol, "`protocol` must not be null.");
    Validate.notNull(uri, "`uri` must not be null.");
    this.protocol = protocol;
    this.scheme = uri.getScheme();
    this.port = uri.getPort();
    this.host = uri.getHost();
    this.path = uri.getPath();
  }

  public @NonNull Protocol getProtocol() {
    return protocol;
  }

  public int getPort() {
    return port;
  }

  public @NonNull String getScheme() {
    return scheme;
  }

  public @NonNull String getHost() {
    return host;
  }

  public @NonNull String getPath() {
    return path;
  }

  public @NonNull ConnectionMethod toTlsEnabled() {
    String secureScheme = scheme;
    switch (scheme.toLowerCase()) {
      case "http":
        secureScheme = "https";
        break;
      case "https":
        return this;
      case "ws":
        secureScheme = "wss";
        break;
      case "wss":
        return this;
      default:
        return this;
    }
    return new ConnectionMethod(protocol, secureScheme, host, port, path);
  }

  public @NonNull URI getUri() {
    try {
      return new URI(scheme, null, host, port, path, null, null);
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
  }

  public boolean isTlsEnabled() {
    switch (scheme.toLowerCase()) {
      case "http":
        return false;
      case "https":
        return true;
      case "ws":
        return false;
      case "wss":
        return true;
      default:
        return false;
    }
  }
}