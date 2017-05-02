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

import chat.viska.commons.events.ExceptionCaughtEvent;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.MaybeSubject;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.net.ssl.SSLException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

/**
 * @since 0.1
 */
public class NettyWebSocketSession extends Session {

  private static final int INBOUND_CACHE_SIZE_BYTES = 1024 * 1024;
  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();
  private static final Transformer DOM_TRANSFORMER_INSTANCE;

  static {
    TransformerFactory factory = TransformerFactory.newInstance();
    try {
      DOM_TRANSFORMER_INSTANCE = factory.newTransformer();
    } catch (TransformerConfigurationException ex) {
      throw new RuntimeException("Error while initializing DOM transformers.", ex);
    }
  }

  private EventLoopGroup nettyEventLoopGroup;
  private SocketChannel nettyChannel;
  private WebSocketClientProtocolHandler nettyWebSocketProtocolHandler;
  private boolean compressionEnabled = false;

  @Override
  protected @NonNull Future<Void> sendStreamOpening() {
    return nettyChannel.writeAndFlush(new TextWebSocketFrame(
        String.format(
            "<open xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\" to=\"%1s\" version=\"1.0\" />",
            getLoginJid().getDomainpart()
        )
    ));
  }

  @Override
  protected @NonNull Future<Void> sendStreamClosing() {
    return nettyChannel.writeAndFlush(new TextWebSocketFrame(
        "<close xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"/>"
    ));
  }

  @Override
  protected void onOpeningConnection()
      throws ConnectionException, InterruptedException {
    final Session thisSession = this;
    final SslContext sslContext;
    try {
      sslContext = getConnectionMethod().isTlsEnabled()
          ? SslContextBuilder.forClient().startTls(false).build()
          : null;
    } catch (SSLException ex) {
      throw new ConnectionException(ex);
    }
    nettyEventLoopGroup = new NioEventLoopGroup();
    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(nettyEventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);
    final MaybeSubject<Boolean> wsHandshakeMaybe = MaybeSubject.create();
    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel ch) throws Exception {
        if (sslContext != null) {
          ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
        }
        ch.pipeline().addLast(new HttpClientCodec());
        ch.pipeline().addLast(new HttpObjectAggregator(
            INBOUND_CACHE_SIZE_BYTES,
                true)
        );
        ch.pipeline().addLast(nettyWebSocketProtocolHandler);
        ch.pipeline().addLast(new WebSocketFrameAggregator(INBOUND_CACHE_SIZE_BYTES));
        ch.pipeline().addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
          boolean wsHandshakeCompleted = false;
          @Override
          protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg)
              throws Exception {
            getXmlPipeline().read(msg.text());
          }
          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
              throws Exception {
            if (!wsHandshakeCompleted && cause instanceof WebSocketHandshakeException) {
              wsHandshakeCompleted = true;
              wsHandshakeMaybe.onError(cause);
            } else {
              triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
            }
          }
          @Override
          public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
              throws Exception {
            if (!wsHandshakeCompleted &&
                evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
              wsHandshakeMaybe.onSuccess(true);
              wsHandshakeCompleted = true;
            } else {
              super.userEventTriggered(ctx, evt);
            }
          }
        });
      }
    });
    final ChannelFuture channelFuture = bootstrap.connect(
        getConnectionMethod().getHost(),
        getConnectionMethod().getPort()
    ).await();
    if (!channelFuture.isSuccess()) {
      nettyChannel.shutdown().awaitUninterruptibly();
      nettyEventLoopGroup.shutdownGracefully().awaitUninterruptibly();
      throw new ConnectionException(channelFuture.cause());
    }
    nettyChannel = (SocketChannel) channelFuture.channel();
    final Boolean isHandshakeDone = wsHandshakeMaybe.blockingGet();
    if (isHandshakeDone == null) {
      nettyChannel.shutdown().awaitUninterruptibly();
      nettyEventLoopGroup.shutdownGracefully().awaitUninterruptibly();
      throw new ConnectionException(wsHandshakeMaybe.getThrowable());
    }
    getXmlPipeline().getOutboundStream().subscribe(new Consumer<Document>() {
      @Override
      public void accept(Document s) throws Exception {
        Source source = new DOMSource(s);
        Writer writer = new StringWriter();
        Result result = new StreamResult(writer);
        DOM_TRANSFORMER_INSTANCE.transform(source, result);
        nettyChannel.writeAndFlush(new TextWebSocketFrame(writer.toString()));
      }
    });
  }

  @Override
  protected void onClosingConnection() {
    nettyWebSocketProtocolHandler.handshaker().close(
        nettyChannel,
        new CloseWebSocketFrame(1000, "Client logging out.")
    );
    nettyChannel.shutdown().awaitUninterruptibly();
    nettyEventLoopGroup.shutdownGracefully().awaitUninterruptibly();
  }

  public NettyWebSocketSession(Jid loginJid) {
    super(loginJid);
  }

  @Override
  public void setConnectionMethod(ConnectionMethod connectionMethod) {
    super.setConnectionMethod(connectionMethod);
    if (connectionMethod == null) {
      return;
    } else if (connectionMethod.getProtocol() != ConnectionMethod.Protocol.WEBSOCKET) {
      throw new IllegalArgumentException();
    }
    nettyWebSocketProtocolHandler = new WebSocketClientProtocolHandler(
        WebSocketClientHandshakerFactory.newHandshaker(
            connectionMethod.getUri(),
            WebSocketVersion.V13,
            "xmpp",
            true,
            null
        ),
        true
    );
  }

  @Override
  public void enableCompression(boolean enabled) {
    switch (getState()) {
      case CONNECTING:
        throw new IllegalStateException();
      case HANDSHAKING:
        throw new IllegalStateException();
      case ONLINE:
        throw new IllegalStateException();
      case DISCONNECTING:
        throw new IllegalStateException();
      default:
        break;
    }
    this.compressionEnabled = enabled;
  }

  @Override
  public boolean isCompressionEnabled() {
    return compressionEnabled;
  }
}