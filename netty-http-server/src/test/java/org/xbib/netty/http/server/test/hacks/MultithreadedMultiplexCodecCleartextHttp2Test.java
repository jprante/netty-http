package org.xbib.netty.http.server.test.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
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
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrameToHttpObjectCodec;
import io.netty.util.AsciiString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * Multithreaded Http2MultiplexCodec demo for cleartext HTTP/2 between a server and a client.
 *
 */
@ExtendWith(NettyHttpTestExtension.class)
class MultithreadedMultiplexCodecCleartextHttp2Test {

    private static final Logger clientLogger = Logger.getLogger("client");
    private static final Logger serverLogger = Logger.getLogger("server");

    private Level level = Level.FINE;

    private InetSocketAddress inetSocketAddress;

    private CompletableFuture<Boolean> settingsPrefaceFuture;

    private CompletableFuture<Boolean> responseFuture;

    private final int threads = 4;

    private final int requestsPerThread = 500;

    private final AtomicInteger responseCounter = new AtomicInteger();

    @Test
    void testMultithreadedMultiplexHttp2() throws Exception {

        inetSocketAddress = new InetSocketAddress("localhost", 8008);
        settingsPrefaceFuture = new CompletableFuture<>();
        responseFuture = new CompletableFuture<>();

        EventLoopGroup serverEventLoopGroup = new NioEventLoopGroup();
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                .group(serverEventLoopGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ChannelPipeline p = ch.pipeline();
                        Http2MultiplexCodec serverMultiplexCodec = Http2MultiplexCodecBuilder.forServer(new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel channel) {
                                    ChannelPipeline p = channel.pipeline();
                                    p.addLast("multiplex-server-frame-converter", new Http2StreamFrameToHttpObjectCodec(true));
                                    p.addLast("multiplex-server-chunk-aggregator", new HttpObjectAggregator(1048576));
                                    p.addLast("multiplex-server-request-handler", new ServerRequestHandler());
                                }
                            })
                            .initialSettings(Http2Settings.defaultSettings())
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
                        p.addLast("server-upgrade", cleartextHttp2ServerUpgradeHandler);
                        p.addLast("server-messages", new ServerMessages());
                    }
                });
            Channel serverChannel = serverBootstrap.bind(inetSocketAddress).sync().channel();
            serverLogger.log(level, "server up, channel = " + serverChannel);

            Bootstrap clientBootstrap = new Bootstrap()
                .group(clientEventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast("client-codec", Http2MultiplexCodecBuilder.forClient(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) {
                                // unused
                                throw new IllegalStateException();
                            }
                        })
                        .initialSettings(Http2Settings.defaultSettings())
                        .build());
                    p.addLast("client-messages", new ClientMessages());
                    }
                });
            Channel clientChannel = clientBootstrap.connect(inetSocketAddress).sync().channel();
            clientLogger.log(level, "client connected, channel = " + clientChannel);

            settingsPrefaceFuture.get(5L, TimeUnit.SECONDS);
            if (!settingsPrefaceFuture.isDone()) {
                throw new RuntimeException("no settings and preface written, unable to continue");
            } else {
                clientLogger.log(level, "settings and preface written, let's start");
            }

            clientLogger.log(Level.INFO, "start");

            ChannelInitializer<Channel> streamChannelInitializer = new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ChannelPipeline p = ch.pipeline();
                    p.addLast("child-client-frame-converter", new Http2StreamFrameToHttpObjectCodec(false));
                    p.addLast("child-client-chunk-aggregator", new HttpObjectAggregator(1048576));
                    p.addLast("child-client-response-handler", new ClientResponseHandler());
                }
            };

            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                final int t = i;
                executorService.submit(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        Http2StreamChannel childChannel = new Http2StreamChannelBootstrap(clientChannel)
                            .handler(streamChannelInitializer).open().syncUninterruptibly().getNow();
                        Http2Headers request = new DefaultHttp2Headers().method(HttpMethod.GET.asciiName())
                                .path("/foobar/" + t + "/" + j)
                                .scheme("http")
                                .authority(inetSocketAddress.getHostName());
                        childChannel.writeAndFlush(new DefaultHttp2HeadersFrame(request, true));
                        //do not close child chqannel after write, a response is expected
                    }
                    clientChannel.flush();
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);

            clientLogger.log(level, "waiting for response future");
            responseFuture.get(60, TimeUnit.SECONDS);
            if (responseFuture.isDone()) {
                clientLogger.log(Level.INFO, "stop");
            }
        } finally {
            clientEventLoopGroup.shutdownGracefully();
            serverEventLoopGroup.shutdownGracefully();
            clientEventLoopGroup.awaitTermination(5, TimeUnit.SECONDS);
            clientLogger.log(level, "client shutdown");
            serverEventLoopGroup.awaitTermination(5, TimeUnit.SECONDS);
            serverLogger.log(level, "server shutdown");
        }
    }

    class ClientResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            responseCounter.getAndIncrement();
            if (responseCounter.get() == threads * requestsPerThread) {
                responseFuture.complete(true);
            }
        }
    }

    class ClientMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // settings
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
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
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK);
            ctx.write(response);
        }
    }

    class ServerMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // settings
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            serverLogger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }
}
