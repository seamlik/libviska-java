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

import chat.viska.commons.DomUtils;
import chat.viska.commons.ExceptionCaughtEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.reactivex.Completable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.CompletableSubject;
import java.util.Collections;
import java.util.Set;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * XMPP session using
 * <a href="https://datatracker.ietf.org/doc/rfc7395">WebSocket</a> connections
 * implemented using Netty.
 *
 * <h2>Compression Support</h2>
 *
 * <table>
 *   <tr>
 *     <th/>
 *     <th>DEFLATE</th>
 *   </tr>
 *   <tr>
 *     <th>Connection</td>
 *     <td>✓</td>
 *   </tr>
 *   <tr>
 *     <th>TLS</td>
 *     <td>✗</td>
 *   </tr>
 *   <tr>
 *     <th>Stream</td>
 *     <td>✗</td>
 *   </tr>
 * </table>
 */
@ThreadSafe
public class NettyWebSocketSession extends DefaultSession {

  private class ConsumerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg)
        throws Exception {
      try {
        feedXmlPipeline(DomUtils.readDocument(msg.text()));
      } catch (SAXException ex) {
        send(new StreamErrorException(
            StreamErrorException.Condition.BAD_FORMAT
        ));
      }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
        throws Exception {
      if (cause instanceof WebSocketHandshakeException) {
        wsHandshakeCompleted.onError(cause);
      }
      triggerEvent(new ExceptionCaughtEvent(nettyChannel, cause));
      super.exceptionCaught(ctx, cause);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
        throws Exception {
      if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
        wsHandshakeCompleted.onComplete();
      }
      super.userEventTriggered(ctx, evt);
    }
  }

  private static final int CACHE_SIZE_BYTES = 1024 * 1024;

  private static final Compression DEFAULT_CONNECTION_COMPRESSION = Compression.DEFLATE;
  private static final Set<Compression> SUPPORTED_CONNECTION_COMPRESSION =
      Collections.singleton(Compression.DEFLATE);

  private final EventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup();
  private Compression connectionCompression;
  private SocketChannel nettyChannel;
  private WebSocketClientProtocolHandler websocketHandler;
  private SslHandler tlsHandler;
  private CompletableSubject wsHandshakeCompleted = CompletableSubject.create();

  private String preprocessOutboundXml(final Document document)
      throws TransformerException {
    final StringBuilder result = new StringBuilder(DomUtils.writeString(document));
    final String rootName = document.getDocumentElement().getLocalName();
    if (document.getDocumentElement().getNamespaceURI() == null) {
      final String prefix = document.getDocumentElement().getPrefix();
      result.insert(rootName.length() + 2, String.format(
          "%1s=\"%2s\" ",
          prefix == null ? "xmlns" : "xmlns:" + prefix,
          Stanza.isStanza(document)
              ? CommonXmlns.STANZA_CLIENT
              : CommonXmlns.STREAM_HEADER
      ));
    }
    return result.toString();
  }

  @CheckReturnValue
  @Nonnull
  @Override
  protected Completable onOpeningConnection(Compression connectionCompression,
                                            Compression tlsCompression) {
    if (connectionCompression == Compression.AUTO) {
      this.connectionCompression = DEFAULT_CONNECTION_COMPRESSION;
    } else if (SUPPORTED_CONNECTION_COMPRESSION.contains(connectionCompression)) {
      this.connectionCompression = connectionCompression;
    } else {
      throw new IllegalArgumentException(
          "Unsupported connection compression " + connectionCompression.toString()
      );
    }

    websocketHandler = new WebSocketClientProtocolHandler(
        WebSocketClientHandshakerFactory.newHandshaker(
            getConnection().getUri(),
            WebSocketVersion.V13,
            "xmpp",
            true,
            new DefaultHttpHeaders()
        ),
        true
    );
    final SslContext sslContext;
    try {
      sslContext = getConnection().isTlsEnabled()
          ? SslContextBuilder.forClient().startTls(false).build()
          : null;
    } catch (SSLException ex) {
      return Completable.error(ex);
    }
    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(nettyEventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);
    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel channel) throws Exception {
        if (sslContext != null) {
          tlsHandler = sslContext.newHandler(channel.alloc());
          channel.pipeline().addLast("tls", tlsHandler);
        }
        channel.pipeline().addLast("http-codec", new HttpClientCodec());
        channel.pipeline().addLast("http-aggregator", new HttpObjectAggregator(
            CACHE_SIZE_BYTES,
                true)
        );
        if (connectionCompression == Compression.DEFLATE) {
          channel.pipeline().addLast("compression", WebSocketClientCompressionHandler.INSTANCE);
        }
        channel.pipeline().addLast("handshaker", websocketHandler);
        channel.pipeline().addLast(
            "websocket-aggregator",
            new WebSocketFrameAggregator(CACHE_SIZE_BYTES)
        );
        channel.pipeline().addLast("consumer", new ConsumerHandler());
      }
    });
    final ChannelFuture channelFuture = bootstrap.connect(
        getConnection().getDomain(),
        getConnection().getPort()
    );
    return Completable.fromFuture(channelFuture).andThen(Completable.fromAction(() -> {
      this.nettyChannel = (SocketChannel) channelFuture.channel();
      this.nettyChannel.closeFuture().addListener(it -> {
        if (!wsHandshakeCompleted.hasComplete()) {
          wsHandshakeCompleted.onError(new ConnectionException(
              "[Netty] Connection terminated before WebSocket handshake completes."
          ));
        }
        triggerEvent(new ConnectionTerminatedEvent(this));
      });
    }).andThen(wsHandshakeCompleted).andThen(Completable.fromAction(() -> {
      getXmlPipelineOutboundStream()
          .subscribeOn(Schedulers.io())
          .subscribe(document ->  {
            final String data = preprocessOutboundXml(document);
            nettyChannel.writeAndFlush(new TextWebSocketFrame(data));
          });
    })));
  }

  @Nonnull
  @CheckReturnValue
  @Override
  protected Completable onClosingConnection() {
    if (nettyChannel != null) {
      if (websocketHandler != null && nettyChannel.isActive()) {
        return Completable.fromFuture(websocketHandler.handshaker().close(
            nettyChannel,
            new CloseWebSocketFrame(1000, null)
        )).andThen(Completable.fromFuture(this.nettyChannel.closeFuture()));
      } else {
        return Completable.fromFuture(this.nettyChannel.close());
      }
    } else {
      return Completable.complete();
    }
  }

  @Nonnull
  @Override
  protected Completable onStartTls() {
    throw new UnsupportedOperationException(
        "WebSocket session does not allow StartTLS."
    );
  }

  @Override
  protected void onStreamCompression(Compression compression) {
    throw new UnsupportedOperationException(
        "This class does not support stream compression."
    );
  }

  @Override
  protected void onDisposing() {
    nettyEventLoopGroup.shutdownGracefully();
  }

  public NettyWebSocketSession(@Nullable final Jid jid,
                               @Nullable final Jid authzId,
                               @Nonnull final Connection connection,
                               final boolean streamManagement) {
    super(jid, authzId, connection, streamManagement);
    if (connection.getProtocol() != Connection.Protocol.WEBSOCKET) {
      throw new IllegalArgumentException("Unsupported connection protocol.");
    }
  }

  @Nullable
  @Override
  public Compression getConnectionCompression() {
    return connectionCompression;
  }

  @Nonnull
  @Override
  public Set<Compression> getSupportedConnectionCompression() {
    return SUPPORTED_CONNECTION_COMPRESSION;
  }

  @Nullable
  @Override
  public Compression getTlsCompression() {
    return null;
  }

  @Nonnull
  @Override
  public Set<Compression> getSupportedTlsCompression() {
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public Compression getStreamCompression() {
    return null;
  }

  @Nonnull
  @Override
  public Set<Compression> getSupportedStreamCompression() {
    return Collections.emptySet();
  }

  @Nullable
  @Override
  public SSLSession getTlsSession() {
    return tlsHandler == null ? null : tlsHandler.engine().getSession();
  }
}