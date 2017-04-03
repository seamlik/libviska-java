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

import chat.viska.netty.ChannelFutureListener;
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
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;
import io.reactivex.subjects.PublishSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.Validate;

/**
 * @since 0.1
 */
public class NettyWebSocketSession extends Session {

  private static final int INBOUND_CACHE_SIZE_BYTES = 1024 * 1024;
  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();

  private EventLoopGroup nettyEventLoopGroup;
  private SocketChannel nettyChannel;
  private WebSocketClientProtocolHandler nettyWebSocketProtocolHandler;
  private final PublishSubject<String> inboundXmppStream = PublishSubject.create();
  private final PublishSubject<String> outboundXmppStream = PublishSubject.create();

  @Override
  protected void onOpeningXmppStream() {

  }

  @Override
  protected void onClosingXmppStream() {

  }

  @Override
  protected Future<Void> onConnecting()
      throws ConnectionMethodNotFoundException, ConnectionException {
    Validate.notNull(nettyWebSocketProtocolHandler, "WebSocket handler not initialized.");

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
          @Override
          protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg)
              throws Exception {
            inboundXmppStream.onNext(msg.text());
          }
        });
        ch.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
          @Override
          protected void channelRead0(ChannelHandlerContext ctx, Object msg)
              throws Exception {
          }

          @Override
          public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
              throws Exception {
            triggerEvent(new ExceptionCaughtEvent(thisSession, cause));
          }
        });
      }
    });
    final ChannelFuture result = bootstrap.connect(
        getConnectionMethod().getHost(),
        getConnectionMethod().getPort()
    );
    result.addListener(new ChannelFutureListener(result) {
      @Override
      public void operationComplete(io.netty.util.concurrent.Future<? super Void> future,
                                    ChannelFuture channelFuture)
          throws Exception {
        nettyChannel = (SocketChannel) channelFuture.channel();
      }
    });
    return result;
  }

  @Override
  protected void onDisconnecting() {
    nettyChannel.shutdown().awaitUninterruptibly();
    nettyEventLoopGroup.shutdownGracefully().awaitUninterruptibly();
  }

  @Override
  protected void onShuttingDown() {

  }

  public NettyWebSocketSession(Jid loginJid) {
    super(loginJid);
  }

  @Override
  public @NonNull Future<List<ConnectionMethod>> queryConnectionMethod() {
    Callable<List<ConnectionMethod>> callable = new Callable<List<ConnectionMethod>>() {
      @Override
      public List<ConnectionMethod> call() throws Exception {
        final List<ConnectionMethod> result = new ArrayList<>();
        try {
          List<ConnectionMethod> methods = ConnectionMethod.queryHostMetaJson(
              getLoginJid().getDomainpart(),
              getProxy()
          );
          for (ConnectionMethod it : methods) {
            if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
              result.add(it);
            }
          }
        } catch (Exception ex) {
          getLoggingManager().log(
              ex,
              Level.WARNING,
              "Failed to find any WebSocket endpoint from `host-meta.json`."
          );
        }
        if (result.isEmpty()) {
          try {
            List<ConnectionMethod> methods = ConnectionMethod.queryHostMetaXml(
                getLoginJid().getDomainpart(),
                getProxy()
            );
            for (ConnectionMethod it : methods) {
              if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
                result.add(it);
              }
            }
          } catch (Exception ex) {
            getLoggingManager().log(
                ex,
                Level.WARNING,
                "Failed to find any WebSocket endpoint from `host-meta`."
            );
          }
        }
        if (result.isEmpty()) {
          try {
            List<ConnectionMethod> methods = ConnectionMethod.queryDnsTxt(
                getLoginJid().getDomainpart(),
                getProxy()
            );
            for (ConnectionMethod it : methods) {
              if (it.getProtocol() == ConnectionMethod.Protocol.WEBSOCKET) {
                result.add(it);
              }
            }
          } catch (Exception ex) {
            getLoggingManager().log(
                ex,
                Level.WARNING,
                "Failed to find any secure WebSocket endpoint from DNS TXT record."
            );
          }
        }
        return result;
      }
    };
    FutureTask<List<ConnectionMethod>> task = new FutureTask<>(callable);
    THREAD_POOL_INSTANCE.submit(task);
    return task;
  }

  @Override
  public void disconnect() {
    super.disconnect();
  }

  @Override
  public Future<Void> send(String xml) {
    outboundXmppStream.onNext(xml);
    return nettyChannel.writeAndFlush(new TextWebSocketFrame(xml));
  }

  @Override
  public synchronized void setConnectionMethod(ConnectionMethod connectionMethod) {
    super.setConnectionMethod(connectionMethod);
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
  public Observable<String> getInboundXmppStream() {
    return inboundXmppStream;
  }

  @Override
  public Observable<String> getOutboundXmppStream() {
    return outboundXmppStream;
  }
}