package org.xbib.netty.http.server.test.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
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

@ExtendWith(NettyHttpTestExtension.class)
class MultithreadedCleartextHttp2Test {

    private static final Logger clientLogger = Logger.getLogger("client");
    private static final Logger serverLogger = Logger.getLogger("server");

    private static final Level level = Level.FINE;

    private InetSocketAddress inetSocketAddress;

    private CompletableFuture<ChannelHandlerContext> settingsPrefaceFuture;

    private CompletableFuture<Boolean> responseFuture;

    private final int threads = 4;

    private final int requestsPerThread = 500;

    private final AtomicInteger responseCounter = new AtomicInteger();

    @Test
    void testMultiThreadedHttp2() throws Exception {

        inetSocketAddress = new InetSocketAddress("localhost", 8008);
        settingsPrefaceFuture = new CompletableFuture<>();
        responseFuture = new CompletableFuture<>();

        EventLoopGroup serverEventLoopGroup = new NioEventLoopGroup();
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();

        try {
            Http2Connection http2ServerConnection = new DefaultHttp2Connection(true);
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverEventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast("server-connection-handler", new HttpToHttp2ConnectionHandlerBuilder()
                                            .initialSettings(Http2Settings.defaultSettings())
                                            .connection(http2ServerConnection)
                                            .frameListener(new InboundHttp2ToHttpAdapterBuilder(http2ServerConnection)
                                                    .maxContentLength(10 * 1024 * 1024)
                                                    .propagateSettings(true)
                                                    .build())
                                            .build())
                                    .addLast("server-handler", new ServerHandler());
                        }
                    })
                    .option(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .option(ChannelOption.SO_RCVBUF, 64 * 1024)
                    .option(ChannelOption.SO_BACKLOG, 8 * 1024)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
                    .childOption(ChannelOption.ALLOCATOR, ByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_SNDBUF, 64 * 1024)
                    .childOption(ChannelOption.SO_RCVBUF, 64 * 1024)
                    .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 64 * 1024));
            Channel serverChannel = serverBootstrap.bind(inetSocketAddress).sync().channel();
            serverLogger.log(level, "server up, channel = " + serverChannel);

            Http2Connection http2ClientConnection = new DefaultHttp2Connection(false);
            Bootstrap clientBootstrap = new Bootstrap()
                .group(clientEventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                            .addLast("client-connection-handler", new HttpToHttp2ConnectionHandlerBuilder()
                                    .initialSettings(Http2Settings.defaultSettings())
                                    .connection(http2ClientConnection)
                                    .frameListener(new InboundHttp2ToHttpAdapterBuilder(http2ClientConnection)
                                            .maxContentLength(10 * 1024 * 1024)
                                            .propagateSettings(true)
                                            .build())
                                    .build())
                            .addLast("client-handler", new ClientHandler());
                    }
                })
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_SNDBUF, 64 * 1024)
                .option(ChannelOption.SO_RCVBUF, 64 * 1024)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5 * 1000)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 64 * 1024));
            Channel clientChannel = clientBootstrap.connect(inetSocketAddress).sync().channel();
            clientLogger.log(level, "client connected, channel = " + clientChannel);

            settingsPrefaceFuture.get(5L, TimeUnit.SECONDS);
            if (!settingsPrefaceFuture.isDone()) {
                throw new RuntimeException("no settings and preface written, unable to continue");
            } else {
                clientLogger.log(level, "settings and preface written, let's start");
            }

            clientLogger.log(Level.INFO, "start");

            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int i = 0; i < threads; i++) {
                final int t = i;
                executorService.submit(() -> {
                    for (int j = 0; j < requestsPerThread; j++) {
                        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                                "http://localhost:8008/foobar/" + t + "/" + j);
                        request.headers().add(HttpHeaderNames.HOST, inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort());
                        clientChannel.write(request);
                    }
                    clientChannel.flush();
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);

            clientLogger.log(level, "waiting");
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

    class ClientHandler extends ChannelDuplexHandler {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
                settingsPrefaceFuture.complete(ctx);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpResponse) {
                responseCounter.getAndIncrement();
            }
            if (responseCounter.get() == threads * requestsPerThread) {
                responseFuture.complete(true);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            clientLogger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }

    class ServerHandler extends ChannelDuplexHandler {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof FullHttpRequest) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK);
                ctx.write(response);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            serverLogger.log(Level.WARNING, cause.getMessage(), cause);
        }
    }
}
