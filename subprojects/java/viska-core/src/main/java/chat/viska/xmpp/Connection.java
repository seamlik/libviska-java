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

import io.netty.channel.AddressedEnvelope;
import io.netty.channel.ReflectiveChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.reactivex.Observable;
import io.reactivex.Single;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

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

  /**
   * Indicates how a {@link Connection} is establishing TLS connections.
   */
  public enum TlsMethod {

    /**
     * Indicates connecting to the server using TLS in the beginning.
     */
    DIRECT,

    /**
     * Indicates TLS is not used.
     */
    NONE,

    /**
     * Indicates using StartTLS.
     */
    STARTTLS
  }

  private static final String QUERY_DNS_TXT = "_xmppconnect.";
  private static final String QUERY_DNR_SRV_STARTTLS = "_xmpp-client._tcp.";
  private static final String QUERY_DNR_SRV_DIRECTTLS = "_xmpps-client._tcp.";
  private static final String KEY_TXT_WEBSOCKET = "_xmpp-client-websocket=";

  private final Protocol protocol;
  private final String scheme;
  private final String domain;
  private final String path;
  private final int port;
  private final TlsMethod tlsMethod;

  private static Single<List<Record>>
  lookupDnsUsingDnsjava(final String query,
                        final int type,
                        @Nullable final List<InetAddress> dns) {
    return Single.fromCallable(() -> {
      final List<? extends Resolver> resolvers = dns == null
          ? Collections.emptyList()
          : Observable.fromIterable(dns).map(it -> {
            final SimpleResolver resolver = new SimpleResolver("localhost");
            resolver.setAddress(it);
            return resolver;
          }).toList().blockingGet();
      final Resolver resolver = resolvers.size() == 0
          ? Lookup.getDefaultResolver()
          : new ExtendedResolver(resolvers.toArray(new Resolver[resolvers.size()]));

      final Lookup lookup = new Lookup(query, type);
      lookup.setResolver(resolver);
      final Record[] records = lookup.run();
      if (records == null) {
        if ("host not found".equals(lookup.getErrorString())) {
          return Collections.emptyList();
        } else if ("type not found".equals(lookup.getErrorString())) {
          return Collections.emptyList();
        } else {
          throw new DnsQueryException(lookup.getErrorString());
        }
      } else {
        return Arrays.asList(records);
      }
    });
  }

  private static Single<List<DnsRecord>>
  lookupDnsUsingNetty(final String query,
                      final DnsRecordType type,
                      @Nullable final Iterable<String> dns) {
    final NioEventLoopGroup threadPool = new NioEventLoopGroup();
    final DnsNameResolverBuilder builder = new DnsNameResolverBuilder(threadPool.next());
    builder.channelFactory(new ReflectiveChannelFactory<>(NioDatagramChannel.class));
    builder.decodeIdn(true);
    if (dns != null) {
      builder.searchDomains(dns);
    }
    return Single.fromFuture(
        builder.build().query(new DefaultDnsQuestion(query, type))
    ).map(AddressedEnvelope::content).map(message -> {
      final int recordsSize = message.count(DnsSection.ANSWER);
      final List<DnsRecord> records = new ArrayList<>(recordsSize);
      for (int it = 0; it < recordsSize; ++it) {
        records.add(message.recordAt(DnsSection.ANSWER, it));
      }
      return records;
    }).doFinally(threadPool::shutdownGracefully);
  }





  /**
   * Queries DNS records for {@link Connection}s. Signals {@link Exception} if
   * DNS queries could not be made. Signals {@link DnsQueryException}.
   * @throws IllegalArgumentException If {@code domain} is invalid.
   */
  public static Single<List<Connection>> queryDns(final String domain,
                                                  final List<InetAddress> dns) {
    final Observable<Connection> startTlsResults;
    final Observable<Connection> directTlsResults;
    final Observable<Connection> txtResults;

    startTlsResults = lookupDnsUsingDnsjava(
        QUERY_DNR_SRV_STARTTLS + domain, Type.SRV, dns
    ).flattenAsObservable(it -> it).cast(SRVRecord.class).map(it -> new Connection(
        it.getTarget().toString(true),
        it.getPort(),
        TlsMethod.STARTTLS
    ));
    directTlsResults = lookupDnsUsingDnsjava(
        QUERY_DNR_SRV_DIRECTTLS + domain, Type.SRV, dns
    ).flattenAsObservable(it -> it).cast(SRVRecord.class).map(it -> new Connection(
        it.getTarget().toString(true),
        it.getPort(),
        TlsMethod.DIRECT
    ));
    txtResults = lookupDnsUsingDnsjava(
        QUERY_DNS_TXT + domain, Type.TXT, dns
    ).flattenAsObservable(
        it -> it
    ).cast(
        TXTRecord.class
    ).map(
        TXTRecord::getStrings
    ).flatMap(
        Observable::fromArray
    ).cast(
        String.class
    ).filter(
        it -> it.startsWith(KEY_TXT_WEBSOCKET)
    ).map(it -> new Connection(
        Protocol.WEBSOCKET,
        new URI(it.substring(it.indexOf('=') + 1))
    ));

    /*
    startTlsResults = lookupDnsUsingNetty(
        QUERY_DNR_SRV_STARTTLS + domain, DnsRecordType.SRV, null
    ).flattenAsObservable(
        it -> it
    ).ofType(
        DefaultDnsRawRecord.class
    ).cast(
        DefaultDnsRawRecord.class
    ).map(
        it -> it.content().toString(StandardCharsets.UTF_8)
    ).map(
        it -> it.split(" ")
    ).map(
        it -> new Connection(it[7], Integer.parseInt(it[6]), TlsMethod.STARTTLS)
    );

    directTlsResults = lookupDnsUsingNetty(
        QUERY_DNR_SRV_DIRECTTLS + domain, DnsRecordType.SRV, null
    ).flattenAsObservable(
        it -> it
    ).ofType(
        DefaultDnsRawRecord.class
    ).cast(
        DefaultDnsRawRecord.class
    ).map(
        DefaultDnsRawRecord::toString
    ).map(
        it -> it.split(" ")
    ).map(
        it -> new Connection(it[7], Integer.parseInt(it[6]), TlsMethod.DIRECT)
    );

    txtResults = lookupDnsUsingNetty(
        QUERY_DNS_TXT + domain, DnsRecordType.TXT, null
    ).flattenAsObservable(
        it -> it
    ).ofType(
        DefaultDnsRawRecord.class
    ).cast(
        DefaultDnsRawRecord.class
    ).map(
        DefaultDnsRawRecord::toString
    ).filter(
        it -> it.startsWith(KEY_TXT_WEBSOCKET)
    ).map(it -> new Connection(
        Protocol.WEBSOCKET,
        new URI(it.substring(it.indexOf('=') + 1))
    ));
    */

    return Observable.concat(
        txtResults, directTlsResults, startTlsResults
    ).toList();
  }

  /**
   * Constructs a {@link Connection} with full server URI.
   * @param port Use {@code -1} to indicate no port.
   * @throws IllegalArgumentException If {@link Protocol#TCP} is specified.
   */
  public Connection(final Protocol protocol,
                    final String scheme,
                    final String domain,
                    final int port,
                    @Nullable final String path) {
    Objects.requireNonNull(protocol, "`protocol` is absent.");
    if (protocol == Protocol.TCP) {
      throw new IllegalArgumentException(
          "TCP protocol is not suitable for this constructor."
      );
    }
    this.protocol = protocol;
    Validate.notBlank(scheme, "`scheme` is absent.");
    this.scheme = StringUtils.isAllLowerCase(scheme) ? scheme : scheme.toLowerCase();
    Validate.notBlank(domain, "`domain` is absent.");
    this.domain = domain;
    this.port = port;
    this.path = StringUtils.defaultIfBlank(path, "");
    this.tlsMethod = TlsMethod.NONE;
  }

  /**
   * Constructs a TCP {@link Connection}.
   * @param port Use {@code -1} to indicate no port.
   * @param tlsMethod Use {@code null} for not using TLS at all.
   */
  public Connection(final String domain,
                    final int port,
                    final TlsMethod tlsMethod) {
    this.protocol = Protocol.TCP;
    Validate.notBlank(domain, "`domain` is absent.");
    this.domain = domain;
    this.port = port;
    this.tlsMethod = tlsMethod;
    scheme = "";
    path = "";
  }

  /**
   * Constructs a {@link Connection} with a full server URI. Convenient method
   * of {@link #Connection(Protocol, String, String, int, String)}.
   */
  public Connection(final Protocol protocol,
                    final URI uri) {
    this(
        protocol,
        uri.getScheme(),
        uri.getHost(),
        uri.getPort(),
        uri.getPath()
    );
  }

  /**
   * Gets the protocol.
   */
  public Protocol getProtocol() {
    return protocol;
  }

  /**
   * Gets the port.
   * @return {@code -1} if no port is specified.
   */
  public int getPort() {
    return port;
  }

  /**
   * Gets the "scheme" part of a URL. Might be {@literal ws} or {@literal wss}.
   */
  public String getScheme() {
    return scheme;
  }

  /**
   * Gets the domain name or IP address of the server.
   */
  public String getDomain() {
    return domain;
  }

  /**
   * Gets the "path" part of a URI.
   */
  public String getPath() {
    return path;
  }

  /**
   * Indicates whether TLS is enabled.
   */
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

  /**
   * Gets how it establishes TLS connections.
   * @return {@code null} if it does not use TLS at all.
   */
  public TlsMethod getTlsMethod() {
    if (this.protocol == Protocol.TCP) {
      return this.tlsMethod;
    } else if (this.tlsMethod == TlsMethod.STARTTLS) {
      return this.tlsMethod;
    } else {
      return isTlsEnabled() ? TlsMethod.DIRECT : TlsMethod.NONE;
    }
  }

  /**
   * Gets the URI represented by this class.
   * @return {@code null} if the protocol is {@link Protocol#TCP}.
   */
  @Nullable
  public URI getUri() {
    if (getProtocol() == Protocol.TCP) {
      return null;
    }
    try {
      return new URI(scheme, null, domain, port, path, null, null);
    } catch (URISyntaxException ex) {
      throw new RuntimeException(ex);
    }
  }
}