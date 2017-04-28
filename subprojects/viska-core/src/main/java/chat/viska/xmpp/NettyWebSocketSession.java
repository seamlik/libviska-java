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
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.GenericFutureListener;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Consumer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import javax.net.ssl.SSLException;

/**
 * @since 0.1
 */
public class NettyWebSocketSession extends Session {

  private static final int INBOUND_CACHE_SIZE_BYTES = 1024 * 1024;
  private static final ExecutorService THREAD_POOL_INSTANCE = Executors.newCachedThreadPool();

  private EventLoopGroup nettyEventLoopGroup;
  private SocketChannel nettyChannel;
  private WebSocketClientProtocolHandler nettyWebSocketProtocolHandler;

  @Override
  protected String generateStreamOpening() {
    return String.format(
        "<open xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\" to=\"%1s\" version=\"1.0\" />",
        getLoginJid().getDomainpart()
    );
  }

  @Override
  protected String generateStreamClosing() {
    return "<close xmlns=\"urn:ietf:params:xml:ns:xmpp-framing\"/>";
  }

  @Override
  protected Future<Void> onOpeningConnection()
      throws ConnectionException {
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
            getXmlPipeline().read(msg);
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
    result.addListener(new GenericFutureListener<io.netty.util.concurrent.Future<? super Void>>() {
      @Override
      public void operationComplete(io.netty.util.concurrent.Future<? super Void> future)
          throws Exception {
        if (future.isSuccess()) {
          nettyChannel = (SocketChannel) result.channel();
          getXmlPipeline().getOutboundStream().subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) throws Exception {
              nettyChannel.writeAndFlush(new TextWebSocketFrame(s));
            }
          });
        }
      }
    });
    return result;
  }

  @Override
  protected Future<Void> onClosingConnection() {
    final FutureTask<Void> task = new FutureTask<>(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        nettyWebSocketProtocolHandler.handshaker().close(
            nettyChannel,
            new CloseWebSocketFrame(1000, "Client logging out.")
        );
        nettyChannel.shutdown().await();
        nettyEventLoopGroup.shutdownGracefully().await();
        return null;
      }
    });
    THREAD_POOL_INSTANCE.submit(task);
    return task;
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
              null
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
                null
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
                null
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
  public void setConnectionMethod(ConnectionMethod connectionMethod) {
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
}