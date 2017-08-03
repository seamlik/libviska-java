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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import io.reactivex.subjects.CompletableSubject;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * XMPP session using WebSocket connections implemented using Netty.
 *
 * <p>This class does not support TLS level compression. For connection level
 * compression, only Deflate is supported.</p>
 */
public class NettyWebSocketSession extends DefaultSession {

  private static final int CACHE_SIZE_BYTES = 1024 * 1024;

  private static final Compression DEFAULT_CONNECTION_COMPRESSION = Compression.DEFLATE;
  private static final Set<Compression> SUPPORTED_CONNECTION_COMPRESSION = new HashSet<>(
      Arrays.asList(
          Compression.DEFLATE
      )
  );

  private final Compression connectionCompression;
  private final AtomicReference<DocumentBuilder> domBuilder = new AtomicReference<>();
  private final AtomicReference<Transformer> domTransformer = new AtomicReference<>();
  private EventLoopGroup nettyEventLoopGroup;
  private SocketChannel nettyChannel;
  private WebSocketClientProtocolHandler websocketHandler;
  private SslHandler tlsHandler;

  private Document preprocessInboundXml(final String txt)
      throws SAXException {
    final Document document;
    try {
      document = domBuilder.get().parse(new InputSource(new StringReader(txt)));
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    document.normalizeDocument();
    return document;
  }

  private String preprocessOutboundXml(final Document document)
      throws TransformerException {
    final Source source = new DOMSource(document);
    final Writer writer = new StringWriter();
    final Result result = new StreamResult(writer);
    domTransformer.get().transform(source, result);
    return writer.toString();
  }

  @Override
  protected void onOpeningConnection()
      throws ConnectionException, InterruptedException {
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
    final DefaultSession thisSession = this;
    final SslContext sslContext;
    try {
      sslContext = getConnection().isTlsEnabled()
          ? SslContextBuilder.forClient().startTls(false).build()
          : null;
    } catch (SSLException ex) {
      throw new ConnectionException(ex);
    }
    nettyEventLoopGroup = new NioEventLoopGroup();
    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(nettyEventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);
    final CompletableSubject wsHandshakeCompleted = CompletableSubject.create();
    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel channel) throws Exception {
        if (sslContext != null) {
          tlsHandler = sslContext.newHandler(channel.alloc());
          channel.pipeline().addLast(tlsHandler);
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
        channel.pipeline().addLast("consumer", new SimpleChannelInboundHandler<TextWebSocketFrame>() {

          @Override
          protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg)
              throws Exception {
            getLogger().fine("[XML received] " + msg.text());
            getXmlPipeline().read(preprocessInboundXml(msg.text()));
          }

          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
              throws Exception {
            if (cause instanceof WebSocketHandshakeException) {
              wsHandshakeCompleted.onError(cause);
            }
            triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
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
        });
      }
    });
    final ChannelFuture channelFuture;
    try {
      getLogger().fine("Connecting to the server...");
      channelFuture = bootstrap.connect(
          getConnection().getDomain(),
          getConnection().getPort()
      ).await();
    } catch (InterruptedException ex) {
      onClosingConnection();
      throw ex;
    }
    if (!channelFuture.isSuccess()) {
      onClosingConnection();
      throw new ConnectionException(channelFuture.cause());
    }
    this.nettyChannel = (SocketChannel) channelFuture.channel();
    try {
      wsHandshakeCompleted.blockingAwait();
    } catch (Exception ex) {
      onClosingConnection();
      if (ex.getCause() instanceof InterruptedException) {
        throw (InterruptedException) ex.getCause();
      } else {
        throw new ConnectionException(ex.getCause());
      }
    }
    this.nettyChannel.closeFuture().addListener(future -> {
      triggerEvent(new ConnectionTerminatedEvent(this));
      this.nettyEventLoopGroup.shutdownGracefully();
    });
    getXmlPipeline()
        .getOutboundStream()
        .subscribe(document ->  {
          final String data = preprocessOutboundXml(document);
          nettyChannel.writeAndFlush(new TextWebSocketFrame(data));
          getLogger().fine("[XML sent] " + data);
        });
  }

  @Override
  protected void onClosingConnection() throws InterruptedException {
    if (nettyChannel != null) {
      if (nettyChannel.isActive() && websocketHandler != null) {
        websocketHandler.handshaker().close(
            nettyChannel,
            new CloseWebSocketFrame(1000, null)
        );
      }
      nettyChannel.shutdown();
    }
    if (nettyEventLoopGroup != null) {
      nettyEventLoopGroup.shutdownGracefully();
    }
  }

  public NettyWebSocketSession(@NonNull final Connection connection,
                               final boolean streamManagement,
                               @Nullable List<String> saslMechanisms,
                               @Nullable Compression tlsCompression,
                               @Nullable Compression connectionCompression,
                               @Nullable Compression streamCompression) {
    super(connection, streamManagement, saslMechanisms, streamCompression);
    this.connectionCompression = connectionCompression == Compression.AUTO
        ? DEFAULT_CONNECTION_COMPRESSION
        : connectionCompression;
    if (this.connectionCompression != null
        && !SUPPORTED_CONNECTION_COMPRESSION.contains(this.connectionCompression)) {
      throw new IllegalArgumentException(
          "Unsupported algorithm for connection level compression"
      );
    }

    final TransformerFactory transformerFactory = TransformerFactory.newInstance();
    final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setIgnoringComments(true);
    builderFactory.setNamespaceAware(true);
    try {
      this.domBuilder.set(builderFactory.newDocumentBuilder());
      this.domTransformer.set(transformerFactory.newTransformer());
      this.domTransformer.get().setOutputProperty(
          OutputKeys.OMIT_XML_DECLARATION,
          "yes"
      );
      this.domTransformer.get().setOutputProperty(
          OutputKeys.INDENT,
          "no"
      );
    } catch (Exception ex) {
      throw new RuntimeException("JVM does not support DOM.", ex);
    }
  }

  @Override
  @Nullable
  public Compression getConnectionCompression() {
    return connectionCompression;
  }

  @Override
  @Nullable
  public Compression getTlsCompression() {
    return null;
  }

  @Override
  @NonNull
  public Certificate[] getTlsLocalCertificates() {
    return tlsHandler == null
        ? new Certificate[0]
        : tlsHandler.engine().getSession().getLocalCertificates();
  }

  @Override
  @NonNull
  public Certificate[] getTlsPeerCertificates() {
    if (tlsHandler == null) {
      return new Certificate[0];
    }
    try {
      return tlsHandler.engine().getSession().getPeerCertificates();
    } catch (SSLPeerUnverifiedException ex) {
      return new Certificate[0];
    }
  }

  @Override
  @NonNull
  public String getTlsProtocol() {
    return tlsHandler == null
        ? ""
        : tlsHandler.engine().getSession().getProtocol();
  }

  @Override
  @NonNull
  public String getTlsCipherSuite() {
    return tlsHandler == null
        ? ""
        : tlsHandler.engine().getSession().getCipherSuite();
  }
}