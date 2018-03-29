/*
 * Copyright 2017-2018 Kai-Chung Yan (殷啟聰)
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
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.codec.xml.XmlAttribute;
import io.netty.handler.codec.xml.XmlDecoder;
import io.netty.handler.codec.xml.XmlDocumentStart;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.codec.xml.XmlFrameDecoder;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.annotations.SchedulerSupport;
import io.reactivex.schedulers.Schedulers;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import rxbeans.ExceptionCaughtEvent;

/**
 * XMPP session using TCP connections implemented using Netty.
 *
 * <p>This class does not support any compression.</p>
 */
@ThreadSafe
@StandardSession.ProtocolSupport(Connection.Protocol.TCP)
public class NettyTcpSession extends StandardSession {

  private class StreamClosingDetector extends ByteToMessageDecoder {

    @Override
    protected void decode(final ChannelHandlerContext ctx,
                          final ByteBuf in,
                          final List<Object> out) {
      if (in.equals(serverStreamClosing)) {
        try {
          in.clear();
          feedXmlPipeline(DomUtils.readDocument(String.format(
              "<close xmlns=\"%1s\"/>",
              CommonXmlns.STREAM_OPENING_WEBSOCKET
          )));
        } catch (SAXException ex) {
          throw new RuntimeException(ex);
        } catch (IllegalStateException ex) {
          triggerEvent(new ExceptionCaughtEvent(this, ex));
        }
      } else {
        out.add(in.readBytes(in.readableBytes()));
      }
    }
  }

  private class StreamOpeningDetector extends SimpleChannelInboundHandler<XmlElementStart> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, XmlElementStart msg)
        throws Exception {
      final SocketChannel nettyChannel = NettyTcpSession.this.nettyChannel;
      if (nettyChannel == null) {
        throw new IllegalStateException();
      }
      nettyChannel.pipeline().replace(
          PIPE_DECODER_XML,
          PIPE_DECODER_XML,
          new ChannelDuplexHandler()
      );

      final String bareNamespace = Observable
          .fromIterable(msg.namespaces())
          .filter(it -> it.prefix().isEmpty())
          .blockingFirst()
          .uri();
      if (!"stream".equals(msg.name())
          || !CommonXmlns.STREAM_HEADER.equals(msg.namespace())
          || !CommonXmlns.STANZA_CLIENT.equals(bareNamespace)) {
        sendError(new StreamErrorException(
            StreamErrorException.Condition.INVALID_NAMESPACE
        ));
        return;
      }
      serverStreamClosing = Unpooled.wrappedBuffer(String.format(
          "</%1s:stream>",
          msg.prefix()
      ).getBytes(StandardCharsets.UTF_8));
      serverStreamPrefix = msg.prefix();
      final Document xml = DomUtils.readDocument(String.format(
          "<open xmlns=\"%1s\"/>",
          CommonXmlns.STREAM_OPENING_WEBSOCKET
      ));
      for (final XmlAttribute it : msg.attributes()) {
        if (it.prefix().isEmpty()) {
          xml.getDocumentElement().setAttribute(it.name(), it.value());
        } else {
          xml.getDocumentElement().setAttribute(
              it.prefix() + ':' + it.name(),
              it.value()
          );
        }
      }

      feedXmlPipeline(xml);
    }
  }

  private static final int MAX_STANZA_SIZE_BYTE = 10000; /* See RFC 6120 § 13.12 4 */
  private static final String PIPE_COMPRESSOR = "compressor";
  private static final String PIPE_TLS = "tls";
  private static final String PIPE_DECODER_XML = "xml-decoder";
  private static final SslContextBuilder TLS_CONTEXT_BUILDER = SslContextBuilder
      .forClient()
      .protocols("TLSv1.2");

  private final EventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup();
  private @MonotonicNonNull SocketChannel nettyChannel;
  private @MonotonicNonNull SslHandler tlsHandler;
  private @MonotonicNonNull ByteBuf serverStreamClosing;
  private String serverStreamPrefix = "";

  /**
   * Default constructor.
   */
  public NettyTcpSession() {
    getXmlPipelineOutboundStream().observeOn(Schedulers.io()).subscribe(it ->  {
      if (nettyChannel == null) {
        return;
      }
      final String data = preprocessOutboundXml(it);
      nettyChannel.writeAndFlush(data);
      final String rootName = it.getDocumentElement().getLocalName();
      final String rootNs = it.getDocumentElement().getNamespaceURI();
      if (CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs) && "open".equals(rootName)) {
        nettyChannel.pipeline().replace(
            PIPE_DECODER_XML,
            PIPE_DECODER_XML,
            new XmlDecoder()
        );
      }
    });
  }

  private Document preprocessInboundXml(final String xml) throws SAXException {
    final StringBuilder builder = new StringBuilder(xml);
    final String openingPrefixBlock = '<' + serverStreamPrefix + ':';
    final String closingPrefixBlock = "</" + serverStreamPrefix + ':';
    final int openingPrefixBlockIdx = builder.indexOf(openingPrefixBlock);
    final int firstXmlnsIdx = builder.indexOf(" xmlns=") + 1;

    if (openingPrefixBlockIdx == 0) {
      // Remove <stream: prefix and add namespace block
      builder.delete(
          1,
          1 + serverStreamPrefix.length() + 1
      );
      final int closingPrefixBlockIdx = builder.indexOf(closingPrefixBlock);
      builder.delete(
          closingPrefixBlockIdx + 2,
          closingPrefixBlockIdx + 2 + serverStreamPrefix.length() + 1
      );
      builder.insert(
          builder.indexOf(">"),
          String.format(
              " xmlns=\"%1s\"",
              CommonXmlns.STREAM_HEADER
          )
      );
    } else if (firstXmlnsIdx < 0 || firstXmlnsIdx > builder.indexOf(">")) {
      // No xmlns for the root, which means a stanza
      builder.insert(
          builder.indexOf(">"),
          String.format(
              " xmlns=\"%1s\"",
              CommonXmlns.STANZA_CLIENT
          )
      );
    }
    return DomUtils.readDocument(builder.toString());
  }

  private String preprocessOutboundXml(
      @UnknownInitialization(NettyTcpSession.class)NettyTcpSession this,
      final Document xml
  ) throws TransformerException {
    final String streamHeaderXmlnsBlock = String.format(
        "xmlns=\"%1s\"",
        CommonXmlns.STREAM_HEADER
    );
    final String rootNs = xml.getDocumentElement().getNamespaceURI();
    final String rootName = xml.getDocumentElement().getLocalName();

    if (CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)) {
      if ("close".equals(rootName)) {
        return "</stream:stream>";
      } else if ("open".equals(rootName)) {
        return convertToTcpStreamOpening(xml);
      } else {
        throw new IllegalArgumentException("Incorrect stream opening XML.");
      }
    } else if (CommonXmlns.STREAM_HEADER.equals(rootNs)) {
      // Elements with header namespace, which mean should be prefixed "stream:"
      final StringBuilder builder = new StringBuilder(DomUtils.writeString(xml));
      final int streamHeaderNamespaceBlockIdx = builder.indexOf(
          streamHeaderXmlnsBlock
      );
      builder.insert(1, serverStreamPrefix + ":");
      builder.delete(
          streamHeaderNamespaceBlockIdx,
          streamHeaderNamespaceBlockIdx + streamHeaderXmlnsBlock.length()
      );
      return builder.toString();
    } else {
      return DomUtils.writeString(xml);
    }
  }

  private static String convertToTcpStreamOpening(final Document xml) {
    final StringBuilder result = new StringBuilder();
    result.append("<stream:stream xmlns=\"")
        .append(CommonXmlns.STANZA_CLIENT)
        .append("\" ");
    result.append("xmlns:stream=\"")
        .append(CommonXmlns.STREAM_HEADER)
        .append("\" ");
    final String id = xml.getDocumentElement().getAttribute("id");
    if (!StringUtils.isBlank(id)) {
      result.append("id=\"").append(id).append("\" ");
    }
    final String recipient = xml.getDocumentElement().getAttribute("to");
    if (StringUtils.isNotBlank(recipient)) {
      result.append("to=\"").append(recipient).append("\" ");
    }
    final String sender = xml.getDocumentElement().getAttribute("from");
    if (StringUtils.isNotBlank(sender)) {
      result.append("from=\"").append(sender).append("\" ");
    }
    final String version = xml.getDocumentElement().getAttribute("version");
    if (StringUtils.isNotBlank(version)) {
      result.append("version=\"").append(version).append("\" ");
    }
    final String lang = xml.getDocumentElement().getAttribute("xml:lang");
    if (StringUtils.isNotBlank(lang)) {
      result.append("xml:lang=\"").append(lang).append("\" ");
    }
    result.append('>');
    return result.toString();
  }

  @SchedulerSupport(SchedulerSupport.CUSTOM)
  @Override
  protected Completable openConnection(final Compression connectionCompression,
                                       final Compression tlsCompression) {
    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(nettyEventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);

    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(final SocketChannel channel) throws SSLException {
        if (getConnection().getTlsMethod() == Connection.TlsMethod.DIRECT) {
          tlsHandler = TLS_CONTEXT_BUILDER.build().newHandler(channel.alloc());
          channel.pipeline().addLast(PIPE_TLS, tlsHandler);
        } else {
          channel.pipeline().addLast(PIPE_TLS, new ChannelDuplexHandler());
        }
        channel.pipeline().addLast(PIPE_COMPRESSOR, new ChannelDuplexHandler());
        channel.pipeline().addLast(
            new DelimiterBasedFrameDecoder(
                MAX_STANZA_SIZE_BYTE,
                false,
                false,
                Unpooled.wrappedBuffer(">".getBytes(StandardCharsets.UTF_8))
            )
        );
        channel.pipeline().addLast(
            new StreamClosingDetector()
        );
        channel.pipeline().addLast(PIPE_DECODER_XML, new XmlDecoder());
        channel.pipeline().addLast(new SimpleChannelInboundHandler<XmlDocumentStart>() {
          @Override
          protected void channelRead0(final ChannelHandlerContext ctx, final XmlDocumentStart msg)
              throws Exception {
            if (!"UTF-8".equalsIgnoreCase(msg.encoding())) {
              sendError(new StreamErrorException(
                  StreamErrorException.Condition.UNSUPPORTED_ENCODING,
                  "Only UTF-8 is supported in XMPP stream."
              ));
            }
          }
        });
        channel.pipeline().addLast(new StreamOpeningDetector());
        channel.pipeline().addLast(new XmlFrameDecoder(MAX_STANZA_SIZE_BYTE));
        channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
        channel.pipeline().addLast(new SimpleChannelInboundHandler<String>() {
          @Override
          protected void channelRead0(final ChannelHandlerContext ctx, final String msg)
              throws Exception {
            try {
              feedXmlPipeline(preprocessInboundXml(msg));
            } catch (SAXException ex) {
              sendError(new StreamErrorException(
                  StreamErrorException.Condition.BAD_FORMAT
              ));
            }
          }
        });
        channel.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
        channel.pipeline().addLast(new SimpleChannelInboundHandler<Object>() {
          @Override
          protected void channelRead0(final ChannelHandlerContext ctx, final Object msg) {}

          @Override
          public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            if (cause instanceof Exception) {
              triggerEvent(new ExceptionCaughtEvent(NettyTcpSession.this, (Exception) cause));
            } else {
              throw new RuntimeException(cause);
            }
          }
        });
      }
    });

    final ChannelFuture channelFuture = bootstrap.connect(
        getConnection().getDomain(),
        getConnection().getPort()
    );
    return Completable.fromFuture(channelFuture).andThen(Completable.fromAction(() -> {
      this.nettyChannel = (SocketChannel) channelFuture.channel();
      nettyChannel.closeFuture().addListener(it -> changeStateToDisconnected());
    }));
  }

  @Override
  public void killConnection() {
    if (this.nettyChannel != null) {
      nettyChannel.close();
    }
  }

  @Override
  protected void deployStreamCompression(final Compression compression) {
    throw new UnsupportedOperationException(
        "This class does not support stream compression."
    );
  }

  @Override
  protected Completable deployTls() {
    if (nettyChannel == null) {
      throw new IllegalStateException();
    }
    try {
      tlsHandler = TLS_CONTEXT_BUILDER.build().newHandler(nettyChannel.alloc());
      nettyChannel.pipeline().replace(PIPE_TLS, PIPE_TLS, this.tlsHandler);
      return Completable.fromFuture(tlsHandler.handshakeFuture());
    } catch (SSLException ex) {
      return Completable.error(ex);
    }
  }

  @Override
  protected void onDisposing() {
    nettyEventLoopGroup.shutdownGracefully();
  }

  @Override
  public Compression getConnectionCompression() {
    return Compression.NONE;
  }

  @Override
  public Compression getTlsCompression() {
    return Compression.NONE;
  }

  @Override
  public Compression getStreamCompression() {
    return Compression.NONE;
  }

  @Nullable
  @Override
  public SSLSession getTlsSession() {
    return tlsHandler == null ? null : tlsHandler.engine().getSession();
  }
}