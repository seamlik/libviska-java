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
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.xml.transform.TransformerException;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * XMPP session using TCP connections implemented using Netty.
 *
 * <p>This class does not support any compression.</p>
 */
public class NettyTcpSession extends DefaultSession {

  private class StreamClosingDetector extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out)
        throws Exception {
      if (in.equals(serverStreamClosing)) {
        try {
          in.clear();
          feedXmlPipeline(DomUtils.readDocument(String.format(
              "<close xmlns=\"%1s\"/>",
              CommonXmlns.STREAM_OPENING_WEBSOCKET
          )));
        } catch (SAXException ex) {
          throw new RuntimeException();
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
        send(new StreamErrorException(
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
      for (XmlAttribute it : msg.attributes()) {
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

  private final EventLoopGroup nettyEventLoopGroup = new NioEventLoopGroup();
  private SocketChannel nettyChannel;
  private SslHandler tlsHandler;
  private ByteBuf serverStreamClosing;
  private String serverStreamPrefix = "";

  private Document preprocessInboundXml(@NonNull final String xml)
      throws SAXException {
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

  private String preprocessOutboundXml(@NonNull final Document xml)
      throws TransformerException {
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

  @NonNull
  private String convertToTcpStreamOpening(@NonNull final Document xml) {
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
    result.append(">");
    return result.toString();
  }

  @Override
  protected Completable onOpeningConnection(final Compression connectionCompression,
                                            final Compression tlsCompression) {
    final Bootstrap bootstrap = new Bootstrap();
    bootstrap.group(nettyEventLoopGroup);
    bootstrap.channel(NioSocketChannel.class);

    bootstrap.handler(new ChannelInitializer<SocketChannel>() {
      @Override
      protected void initChannel(SocketChannel channel) throws Exception {
        if (getConnection().getTlsMethod() == Connection.TlsMethod.DIRECT) {
          tlsHandler = SslContextBuilder
              .forClient()
              .build()
              .newHandler(channel.alloc());
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
          protected void channelRead0(ChannelHandlerContext ctx, XmlDocumentStart msg)
              throws Exception {
            if (!"UTF-8".equalsIgnoreCase(msg.encoding())) {
              send(new StreamErrorException(
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
          protected void channelRead0(ChannelHandlerContext ctx, String msg)
              throws Exception {
            try {
              feedXmlPipeline(preprocessInboundXml(msg));
            } catch (SAXException ex) {
              send(new StreamErrorException(
                  StreamErrorException.Condition.BAD_FORMAT
              ));
            }
          }
        });
        channel.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
      }
    });

    final ChannelFuture channelFuture = bootstrap.connect(
        getConnection().getDomain(),
        getConnection().getPort()
    );
    return Completable.fromFuture(channelFuture).andThen(Completable.fromAction(() -> {
      this.nettyChannel = (SocketChannel) channelFuture.channel();
      this.nettyChannel.closeFuture().addListener(it -> {
        triggerEvent(new ConnectionTerminatedEvent(this));
      });
    }).andThen(Completable.fromAction(() ->
        getXmlPipelineOutboundStream().subscribe(it ->  {
          final String data = preprocessOutboundXml(it);
          nettyChannel.writeAndFlush(data);
          final String rootName = it.getDocumentElement().getLocalName();
          final String rootNs = it.getDocumentElement().getNamespaceURI();
          if (CommonXmlns.STREAM_OPENING_WEBSOCKET.equals(rootNs)
              && "open".equals(rootName)) {
            nettyChannel.pipeline().replace(
                PIPE_DECODER_XML,
                PIPE_DECODER_XML,
                new XmlDecoder()
            );
          }
        })
    )));
  }

  @Override
  protected Completable onClosingConnection() {
    if (this.nettyChannel != null) {
      return Completable.fromFuture(this.nettyChannel.close());
    } else {
      return Completable.complete();
    }
  }

  @Override
  protected void onStreamCompression(Compression compression) {
    throw new UnsupportedOperationException(
        "This class does not support stream compression."
    );
  }

  @Override
  protected Completable onStartTls() {
    try {
      this.tlsHandler = SslContextBuilder
          .forClient()
          .build()
          .newHandler(nettyChannel.alloc());
    } catch (SSLException ex) {
      return Completable.error(ex);
    }
    nettyChannel.pipeline().replace(PIPE_TLS, PIPE_TLS, this.tlsHandler);
    return Completable.fromFuture(this.tlsHandler.handshakeFuture());
  }

  @Override
  protected void onDisposing() {
    nettyEventLoopGroup.shutdownGracefully();
  }

  public NettyTcpSession(@Nullable final Jid jid,
                         @Nullable final Jid authzId,
                         @NonNull final Connection connection,
                         final boolean streamManagement) {
    super(jid, authzId, connection, streamManagement);
    if (connection.getProtocol() != Connection.Protocol.TCP) {
      throw new IllegalArgumentException("Only TCP protocol supported.");
    }
  }

  @Override
  public Compression getConnectionCompression() {
    return null;
  }

  @Override
  public Set<Compression> getSupportedConnectionCompression() {
    return Collections.emptySet();
  }

  @Override
  public Compression getTlsCompression() {
    return null;
  }

  @Override
  public Set<Compression> getSupportedTlsCompression() {
    return Collections.emptySet();
  }

  @Override
  public Compression getStreamCompression() {
    return null;
  }

  @Override
  public Set<Compression> getSupportedStreamCompression() {
    return Collections.emptySet();
  }

  @Override
  public SSLSession getTlsSession() {
    return tlsHandler == null ? null : tlsHandler.engine().getSession();
  }
}