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

import chat.viska.commons.DomUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
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
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;
import org.xml.sax.SAXException;

/**
 * Connection method to an XMPP server.
 */
public class Connection {

  /**
   * The transport protocol used for the network connection.
   */
  public enum Protocol {

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
     * WebSocket</a>.
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

  public enum TlsMethod {
    DIRECT,
    STARTTLS
  }

  private static final String DNS_TXT_QUERY = "_xmppconnect.";
  private static final String DNR_SRV_QUERY = "_xmpp-client._tcp.";
  private static final String DNR_SRV_SECURE_QUERY = "_xmpps-client._tcp.";

  private final Protocol protocol;
  private final String scheme;
  private final String domain;
  private final String path;
  private final int port;
  private final TlsMethod tlsMethod;

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

  private static @NonNull Single<JsonObject>
  downloadHostMetaJson(@NonNull final String domain, @Nullable final Proxy proxy)
      throws MalformedURLException {
    final URL hostMetaUrl = getHostMetaUrl(domain, true);
    return Single.fromCallable(() -> {
      try (
          final InputStream stream = proxy == null
              ? hostMetaUrl.openStream()
              : hostMetaUrl.openConnection(proxy).getInputStream();
          final InputStreamReader reader = new InputStreamReader(
              stream, StandardCharsets.UTF_8
          );
          ) {
        return new JsonParser().parse(reader).getAsJsonObject();
      } catch (JsonParseException ex) {
        throw new InvalidHostMetaException(ex);
      }
    });
  }

  private static @NonNull Single<Document>
  downloadHostMetaXml(@NonNull final String domain, @Nullable final Proxy proxy)
      throws MalformedURLException {
    final URL hostMetaUrl = getHostMetaUrl(domain, false);
    return Single.fromCallable(() -> {
      try (
          final InputStream stream = proxy == null
              ? hostMetaUrl.openStream()
              : hostMetaUrl.openConnection(proxy).getInputStream();
          final InputStreamReader reader = new InputStreamReader(
              stream, StandardCharsets.UTF_8
          );
      ) {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);
      } catch (SAXException ex) {
        throw new InvalidHostMetaException(ex);
      }
    });
  }

  private static List<Connection> queryHostMetaXml(@NonNull Document hostMeta) {
    return Observable.fromIterable(
        DomUtils.convertToList(hostMeta.getDocumentElement().getElementsByTagName("Link"))
    ).cast(Element.class).filter(
        it -> CommonXmlns.WEBSOCKET.equals(it.getAttribute("rel"))
    ).map(it -> new Connection(
        Protocol.WEBSOCKET,
        new URI(it.getAttribute("href"))
    )).toList().blockingGet();
  }

  public static Single<List<Connection>>
  queryHostMetaXml(@NonNull final String domain, @Nullable final Proxy proxy)
      throws MalformedURLException {
    return downloadHostMetaXml(domain, proxy)
        .map(Connection::queryHostMetaXml);
  }

  public static Single<List<Connection>>
  queryHostMetaXml(@NonNull final InputStream hostMeta)
      throws IOException, InvalidHostMetaException {
    return Single.fromCallable(() -> {
      try {
        return DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(hostMeta);
      } catch (SAXException ex) {
        throw new InvalidHostMetaException(ex);
      }
    }).map(Connection::queryHostMetaXml);
  }

  private static List<Connection>
  queryHostMetaJson(@NonNull final JsonObject hostMeta) {
    return Observable.fromIterable(
        hostMeta.getAsJsonObject().getAsJsonArray("links")
    ).filter(
        it -> it.getAsJsonObject()
            .getAsJsonPrimitive("rel")
            .getAsString()
            .equals(CommonXmlns.WEBSOCKET)
    ).map(element -> new Connection(
        Protocol.WEBSOCKET,
        new URI(
            element.getAsJsonObject()
                   .getAsJsonPrimitive("href")
                   .getAsString()
        )
    )).toList().blockingGet();
  }

  public static Single<List<Connection>>
  queryHostMetaJson(@NonNull final Reader hostMeta) {
    return Single.fromCallable(
        () -> new JsonParser().parse(hostMeta).getAsJsonObject()
    ).map(Connection::queryHostMetaJson);
  }

  public static Single<List<Connection>>
  queryHostMetaJson(@NonNull final String domain, @Nullable final Proxy proxy)
      throws InvalidHostMetaException, IOException {
    return downloadHostMetaJson(domain, proxy).map(Connection::queryHostMetaJson);
  }

  public static Single<List<Connection>> queryDns(@NonNull final String domain) {
    return Single.fromCallable(() -> {
      final List<Connection> result = new ArrayList<>();
      final Record[] tcpRecords = new Lookup(
          DNR_SRV_QUERY + domain, Type.SRV
      ).run();
      if (tcpRecords != null) {
        Observable.fromArray(tcpRecords).cast(SRVRecord.class).forEach(it -> {
          result.add(new Connection(
              it.getTarget().toString(true),
              it.getPort(),
              TlsMethod.STARTTLS
          ));
        });
      }
      final Record[] tcpTlsRecords = new Lookup(
          DNR_SRV_SECURE_QUERY + domain, Type.SRV
      ).run();
      if (tcpTlsRecords != null) {
        Observable.fromArray(tcpTlsRecords).cast(SRVRecord.class).forEach(it -> {
          result.add(new Connection(
              it.getTarget().toString(true),
              it.getPort(),
              TlsMethod.DIRECT
          ));
        });
      }
      final Record[] txtRecords = new Lookup(
          DNS_TXT_QUERY + domain, Type.TXT
      ).run();
      if (txtRecords != null) {
        Observable.fromArray(txtRecords).cast(TXTRecord.class)
            .map(TXTRecord::getStrings)
            .forEach(it -> {
              final String txt = (String) it.get(0);
              if (txt.startsWith("_xmpp-client-websocket=")) {
                result.add(new Connection(
                    Protocol.WEBSOCKET,
                    new URI(txt.substring(txt.indexOf('=') + 1))
                ));
              }
            });
      }
      return result;
    });
  }

  public Connection(@NonNull final Protocol protocol,
                    @NonNull final String scheme,
                    @NonNull final String domain,
                    final int port,
                    @Nullable final String path) {
    if (protocol == null || protocol == Protocol.TCP) {
      throw new IllegalArgumentException();
    }
    this.protocol = protocol;
    Validate.notBlank(scheme, "`scheme` is absent.");
    this.scheme = StringUtils.isAllLowerCase(scheme) ? scheme : scheme.toLowerCase();
    Validate.notBlank(domain, "`domain` is absent.");
    this.domain = domain;
    this.port = port;
    this.path = StringUtils.defaultIfBlank(path, "");
    this.tlsMethod = null;
  }

  public Connection(@NonNull final String domain,
                    final int port,
                    @Nullable final TlsMethod tlsMethod) {
    this.protocol = Protocol.TCP;
    Validate.notBlank(domain, "`domain` is absent.");
    this.domain = domain;
    this.port = port;
    this.tlsMethod = tlsMethod;
    scheme = "";
    path = "";
  }

  public Connection(@NonNull final Protocol protocol,
                    @NonNull final URI uri) {
    this(
        protocol,
        uri.getScheme(),
        uri.getHost(),
        uri.getPort(),
        uri.getPath()
    );
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
      return tlsMethod != null;
    }
    switch (scheme) {
      case "ws":
        return false;
      case "wss":
        return true;
      default:
        return false;
    }
  }

  @NonNull
  public TlsMethod getTlsMethod() {
    if (this.protocol == Protocol.TCP) {
      return this.tlsMethod;
    } else if (this.tlsMethod == TlsMethod.STARTTLS) {
      return this.tlsMethod;
    } else {
      return isTlsEnabled() ? TlsMethod.DIRECT : null;
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
}