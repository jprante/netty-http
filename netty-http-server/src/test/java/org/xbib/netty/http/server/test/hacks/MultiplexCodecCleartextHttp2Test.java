package org.xbib.netty.http.server.test.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.server.test.NettyHttpExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * Http2MultiplexCodec demo for cleartext HTTP/2 between a server and a client.
 *
 * What is HTTP/2 multiplex codec?
 *
 * codec-http2 currently has two flavors of APIs:
 *
 * <ul>
 *     <li>Http2ConnectionHandler and FrameListener - the initial API, lots of hours/usage on this code,
 * low object allocation, not canonical Netty design (extensibility via the channel pipeline is challenging)</li>
 *     <li>Http2FrameCodec and Http2MultiplexCodec - new API design which is more canonical Netty,
 *     more object allocation, not as much hours/usage. The FrameListener API exposure is minimized
 *     from the Http2MultiplexCodec and ideally its usage of the FrameListener is an implementation detail.</li>
 * </ul>
 *
 *
 */
@ExtendWith(NettyHttpExtension.class)
class MultiplexCodecCleartextHttp2Test {

    private static final Logger clientLogger = Logger.getLogger("client");
    private static final Logger serverLogger = Logger.getLogger("server");

    private final InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 8443);

    private final CompletableFuture<Boolean> settingsPrefaceFuture = new CompletableFuture<>();

    private final CompletableFuture<Boolean> responseFuture = new CompletableFuture<>();

    @Test
    void testMultiplexHttp2() throws Exception {
        Http2FrameLogger serverFrameLogger = new Http2FrameLogger(LogLevel.INFO, "server");
        Http2FrameLogger clientFrameLogger = new Http2FrameLogger(LogLevel.INFO, "client");
        EventLoopGroup serverEventLoopGroup = new NioEventLoopGroup();
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(serverEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .handler(serverFrameLogger)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        Http2MultiplexCodec serverMultiplexCodec = Http2MultiplexCodecBuilder.forServer(new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel channel) {
                                    ChannelPipeline p = channel.pipeline();
                                    p.addLast("multiplex-server-traffic", new TrafficLoggingHandler("multiplex-server-traffic", LogLevel.INFO));
                                    p.addLast("multiplex-server-frame-converter", new Http2StreamFrameToHttpObjectCodec(true));
                                    p.addLast("multiplex-server-chunk-aggregator", new HttpObjectAggregator(1048576));
                                    p.addLast("multiplex-server-request-handler", new ServerRequestHandler());
                                }
                            })
                            .initialSettings(Http2Settings.defaultSettings())
                            .frameLogger(serverFrameLogger)
                            .build();
                        HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
                            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                                return new Http2ServerUpgradeCodec("server-codec", serverMultiplexCodec);
                            } else {
                                return null;
                            }
                        };
                        HttpServerCodec sourceCodec = new HttpServerCodec();
                        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory);
                        CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                                new CleartextHttp2ServerUpgradeHandler(sourceCodec, upgradeHandler, serverMultiplexCodec);
                        p.addLast("server-traffic", new TrafficLoggingHandler("server-traffic", LogLevel.INFO));
                        p.addLast("server-upgrade", cleartextHttp2ServerUpgradeHandler);
                        p.addLast("server-messages", new ServerMessages());
                    }
                });
            Channel serverChannel = serverBootstrap.bind(inetSocketAddress).sync().channel();
            serverLogger.log(Level.INFO, "server up, channel = " + serverChannel);

            Http2MultiplexCodec clientMultiplexCodec = Http2MultiplexCodecBuilder.forClient(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        // unused
                        throw new IllegalStateException();
                    }
                })
                .initialSettings(Http2Settings.defaultSettings())
                .frameLogger(clientFrameLogger)
                .build();
            Bootstrap clientBootstrap = new Bootstrap()
                .group(clientEventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("client-traffic", new TrafficLoggingHandler("client-traffic", LogLevel.INFO));
                        p.addLast("client-codec", clientMultiplexCodec);
                        p.addLast("client-messages", new ClientMessages());
                    }
                });
            Channel clientChannel = clientBootstrap.connect(inetSocketAddress).sync().channel();
            clientLogger.log(Level.INFO, "client connected, channel = " + clientChannel);

            settingsPrefaceFuture.get(5L, TimeUnit.SECONDS);
            if (!settingsPrefaceFuture.isDone()) {
                throw new RuntimeException("no settings and preface written, unable to continue");
            } else {
                clientLogger.log(Level.INFO, "settings and preface written");
            }
            // after settings/preface event, start child channel write
            Http2StreamChannel childChannel = new Http2StreamChannelBootstrap(clientChannel)
                .handler(new ChannelInitializer<Channel>() {
                     @Override
                     protected void initChannel(Channel ch)  {
                         ChannelPipeline p = ch.pipeline();
                         p.addLast("child-client-traffic", new TrafficLoggingHandler("child-client-traffic", LogLevel.INFO));
                         p.addLast("child-client-frame-converter", new Http2StreamFrameToHttpObjectCodec(false));
                         p.addLast("child-client-chunk-aggregator", new HttpObjectAggregator(1048576));
                         p.addLast("child-client-response-handler", new ClientResponseHandler());
                     }
                 }).open().syncUninterruptibly().getNow();
            Http2Headers request = new DefaultHttp2Headers()
                    .method(HttpMethod.GET.asciiName())
                    .path("/foobar/0/0")
                    .scheme("http")
                    .authority(inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort());
            childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(request, true));
            clientLogger.log(Level.INFO, "waiting max. 10 seconds");
            responseFuture.get(10, TimeUnit.SECONDS);
            if (responseFuture.isDone()) {
                clientLogger.log(Level.INFO, "done!");
            }
        } finally {
            clientEventLoopGroup.shutdownGracefully();
            serverEventLoopGroup.shutdownGracefully();
            clientEventLoopGroup.awaitTermination(5, TimeUnit.SECONDS);
            clientLogger.log(Level.INFO, "client shutdown");
            serverEventLoopGroup.awaitTermination(5, TimeUnit.SECONDS);
            serverLogger.log(Level.INFO, "server shutdown");
        }
    }

    class ClientResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            clientLogger.log(Level.INFO, "response received on client: " + msg);
            responseFuture.complete(true);
        }
    }

    class ClientMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            clientLogger.log(Level.FINE, "got client msg " + msg + " class = " + msg.getClass());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            clientLogger.log(Level.FINE, "got client user event " + evt);
            if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
                settingsPrefaceFuture.complete(true);
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            clientLogger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }

    class ServerRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
            serverLogger.log(Level.INFO, "request received on server: " + msg +
                    " path = " + msg);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK);
            serverLogger.log(Level.INFO, "writing server response: " + response);
            ctx.writeAndFlush(response);
        }
    }

    class ServerMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            serverLogger.log(Level.FINE, "got server msg " + msg + " class = " + msg.getClass());
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            serverLogger.log(Level.FINE, "got server user event " + evt);
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            serverLogger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }

    class TrafficLoggingHandler extends LoggingHandler {

        TrafficLoggingHandler(String name, LogLevel level) {
            super(name, level);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf && !((ByteBuf) msg).isReadable()) {
                ctx.write(msg, promise);
            } else {
                super.write(ctx, msg, promise);
            }
        }
    }
}
