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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Connection method to an XMPP server.
 */
public class Connection {

  /**
   * The transport protocol used for the network connection.
   */
  public enum Protocol {
    /**
     * <a href="https://xmpp.org/extensions/xep-0206.html">XEP-0206: XMPP Over
     * BOSH</a>.
     */
    BOSH,

    /**
     * Primary connection protocol defined in
     * <a href="https://datatracker.ietf.org/doc/rfc6120">RFC 6120: Extensible
     * Messaging and Presence Protocol (XMPP): Core</a>. This protocol supports
     * StartTLS.
     */
    TCP,

    /**
     * <a href="https://datatracker.ietf.org/doc/rfc7395">RFC 7395: An
     * Extensible Messaging and Presence Protocol (XMPP) Subprotocol for
     * WebSocket</a>
     */
    WEBSOCKET;

    @Override
    public String toString() {
      switch (this) {
        case WEBSOCKET:
          return "WebSocket";
        default:
          return name();
      }
    }
  }

  private final Protocol protocol;
  private final String scheme;
  private final String domain;
  private final String path;
  private final int port;
  private final Boolean tlsEnabled;
  private final Boolean startTlsEnabled;

  public static final int DEFAULT_TCP_PORT_XMPP = 5222;

  /**
   * Returns a URL to the domain-meta file.
   * <p>This method forces to use HTTPS URL for security reasons.</p>
   * @param host The domain of the server, might be an IP address, a domain
   *               name, {@code localhost}, etc.. If it specifies a protocol
   *               other than HTTPS, HTTPS will still be used.
   * @param json Indicates whether to get the JSON version or the XML version.
   * @throws MalformedURLException If {@code domain} is invalid.
   */
  private static @NonNull URL getHostMetaUrl(@NonNull String host, boolean json)
      throws MalformedURLException {
    final String hostMetaPath = "/.well-known/domain-meta" + (json ? ".json" : "");
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
    JsonObject hostMeta;
    try {
      final URL hostMetaUrl = getHostMetaUrl(host, true);
      stream = proxy == null ? hostMetaUrl.openStream()
                             : hostMetaUrl.openConnection(proxy).getInputStream();
      reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
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
      reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
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

  private static List<Connection> queryHostMetaXml(@NonNull Document hostMeta)
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
        return rel.equals(CommonXmlns.BOSH) || rel.equals(CommonXmlns.WEBSOCKET);
      }
    }).map(new Function<Element, Connection>() {
      @Override
      public Connection apply(Element element) throws Exception {
        Protocol protocol = null;
        switch (element.getAttribute("rel")) {
          case CommonXmlns.BOSH:
            protocol = Protocol.BOSH;
            break;
          case CommonXmlns.WEBSOCKET:
            protocol = Protocol.WEBSOCKET;
            break;
          default:
            break;
        }
        return new Connection(protocol, new URI(element.getAttribute("href")));
      }
    }).toList().blockingGet();
  }

  public static List<Connection> queryHostMetaXml(@NonNull String domain,
                                                  @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notBlank(domain, "`domain` must not be blank.");
    return queryHostMetaXml(downloadHostMetaXml(domain, proxy));
  }

  public static List<Connection> queryHostMetaXml(@NonNull InputStream hostMeta)
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

  private static List<Connection> queryHostMetaJson(@NonNull JsonObject hostMeta) {
    Validate.notNull(hostMeta, "`hostMeta` must not be null.");
    return Observable.fromIterable(
        hostMeta.getAsJsonObject().getAsJsonArray("links")
    ).filter(new Predicate<JsonElement>() {
      @Override
      public boolean test(JsonElement element) throws Exception {
        final String rel = element.getAsJsonObject()
                                  .getAsJsonPrimitive("rel")
                                  .getAsString();
        return rel.equals(CommonXmlns.BOSH) || rel.equals(CommonXmlns.WEBSOCKET);
      }
    }).map(new Function<JsonElement, Connection>() {
      @Override
      public Connection apply(JsonElement element) throws Exception {
        Protocol protocol = null;
        final String rel = element.getAsJsonObject()
                                  .getAsJsonPrimitive("rel")
                                  .getAsString();
        switch (rel) {
          case CommonXmlns.BOSH:
            protocol = Protocol.BOSH;
            break;
          case CommonXmlns.WEBSOCKET:
            protocol = Protocol.WEBSOCKET;
            break;
          default:
            break;
        }
        return new Connection(
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

  public static List<Connection> queryHostMetaJson(@NonNull Reader hostMeta) {
    return queryHostMetaJson(new JsonParser().parse(hostMeta).getAsJsonObject());
  }

  public static List<Connection> queryHostMetaJson(@NonNull String domain,
                                                   @Nullable Proxy proxy)
      throws InvalidHostMetaException, IOException {
    Validate.notBlank(domain, "`domain` must not be blank.");
    return queryHostMetaJson(downloadHostMetaJson(domain, proxy));
  }

  public static List<Connection> queryDnsTxt(@NonNull String domain) {
    Validate.notNull(domain);
    throw new RuntimeException();
  }

  public static Future<List<Connection>> queryDnsSrv(@NonNull String domain) {
    Validate.notNull(domain);
    throw new RuntimeException();
  }

  public Connection(final @NonNull Protocol protocol,
                    final @NonNull String scheme,
                    final @NonNull String domain,
                    final int port,
                    final @Nullable String path) {
    if (protocol == null || protocol == Protocol.TCP) {
      throw new IllegalArgumentException();
    }
    this.protocol = protocol;
    final String schemeTrimmed = scheme == null ? "" : scheme.trim();
    if (schemeTrimmed.isEmpty()) {
      throw new IllegalArgumentException();
    }
    this.scheme = scheme;
    final String domainTrimmed = domain == null ? "" : domain.trim();
    if (domainTrimmed.isEmpty()) {
      throw new IllegalArgumentException();
    }
    this.domain = domainTrimmed;
    this.port = port;
    final String pathTrimmed = path.trim();
    this.path = pathTrimmed.isEmpty() ? "" : pathTrimmed;

    tlsEnabled = null;
    startTlsEnabled = null;
  }

  public Connection(final @NonNull String domain,
                    final int port,
                    final boolean tlsEnabled,
                    final boolean startTlsEnabled) {
    this.protocol = Protocol.TCP;
    final String domainTrimmed = domain == null ? "" : domain.trim();
    if (domainTrimmed.isEmpty()) {
      throw new IllegalArgumentException();
    }
    this.domain = domainTrimmed;
    this.port = port;
    this.tlsEnabled = tlsEnabled;
    if (tlsEnabled) {
      this.startTlsEnabled = startTlsEnabled;
    } else {
      this.startTlsEnabled = null;
    }

    scheme = "";
    path = "";
  }

  public Connection(@NonNull Protocol protocol, @NonNull URI uri) {
    this(protocol, uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath());
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

  public @NonNull String getDomain() {
    return domain;
  }

  public @NonNull String getPath() {
    return path;
  }

  public boolean isTlsEnabled() {
    if (protocol == Protocol.TCP) {
      return tlsEnabled;
    }
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

  public boolean isStartTlsRequired() {
    if (protocol == Protocol.TCP && isTlsEnabled()) {
      return startTlsEnabled;
    } else {
      return false;
    }
  }

  @NonNull
  public URI getUri() {
    try {
      return new URI(scheme, null, domain, port, path, null, null);
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
  }

  public @NonNull
  Connection toTlsEnabled(final @Nullable Boolean enableStartTls) {
    if (isTlsEnabled()) {
      return this;
    }
    if (protocol == Protocol.TCP) {
      Objects.requireNonNull(enableStartTls);
      return new Connection(domain, port, tlsEnabled, enableStartTls);
    }
    final String secureScheme;
    switch (scheme.toLowerCase()) {
      case "http":
        secureScheme = "https";
        break;
      case "ws":
        secureScheme = "wss";
        break;
      default:
        return this;
    }
    return new Connection(protocol, secureScheme, domain, port, path);
  }
}