package org.xbib.netty.http.server.test.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
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
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.server.test.TestBase;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Ignore
public class CleartextHttp2Test extends TestBase {

    private static final Logger clientLogger = Logger.getLogger("client");
    private static final Logger serverLogger = Logger.getLogger("server");

    private static final LogLevel logLevel = LogLevel.DEBUG;
    private static final Level level = Level.FINE;

    private static final Http2FrameLogger serverFrameLogger = new Http2FrameLogger(logLevel, "server");
    private static final Http2FrameLogger clientFrameLogger = new Http2FrameLogger(logLevel, "client");

    private CompletableFuture<ChannelHandlerContext> settingsPrefaceFuture;

    private CompletableFuture<Boolean> completableFuture;

    @Test
    public void testHttp2() throws Exception {

        final InetSocketAddress inetSocketAddress = new InetSocketAddress("localhost", 8008);

        settingsPrefaceFuture = new CompletableFuture<>();

        completableFuture = new CompletableFuture<>();

        EventLoopGroup serverEventLoopGroup = new NioEventLoopGroup();
        EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup();

        try {
            Http2Connection http2ServerConnection = new DefaultHttp2Connection(true);
            ServerBootstrap serverBootstrap = new ServerBootstrap()
                    .group(serverEventLoopGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(serverFrameLogger)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline()
                                    .addLast("server-traffic", new TrafficLoggingHandler("server-traffic", logLevel))
                                    .addLast("server-connection-handler", new HttpToHttp2ConnectionHandlerBuilder()
                                            .initialSettings(Http2Settings.defaultSettings())
                                            .connection(http2ServerConnection)
                                            .frameListener(new InboundHttp2ToHttpAdapterBuilder(http2ServerConnection)
                                                    .maxContentLength(10 * 1024 * 1024)
                                                    .propagateSettings(true)
                                                    .build())
                                            .frameLogger(serverFrameLogger)
                                            .build())
                                    .addLast("server-handler", new ServerHandler());
                        }
                    });
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
                                    .addLast("client-traffic", new TrafficLoggingHandler("client-traffic", logLevel))
                                    .addLast("client-connection-handler", new HttpToHttp2ConnectionHandlerBuilder()
                                            .initialSettings(Http2Settings.defaultSettings())
                                            .connection(http2ClientConnection)
                                            .frameListener(new InboundHttp2ToHttpAdapterBuilder(http2ClientConnection)
                                                    .maxContentLength(10 * 1024 * 1024)
                                                    .propagateSettings(true)
                                                    .build())
                                            .frameLogger(clientFrameLogger)
                                            .build())
                                    .addLast("client-handler", new ClientHandler());
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

            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    "http://localhost:8008/foobar/0/0");
            request.headers().add(HttpHeaderNames.HOST, inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort());
            clientChannel.writeAndFlush(request);

            clientLogger.log(level, "waiting");
            completableFuture.get(10, TimeUnit.SECONDS);
            if (completableFuture.isDone()) {
                clientLogger.log(Level.INFO, "done");
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
            clientLogger.log(level, "got event on client " + evt);
            if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
                settingsPrefaceFuture.complete(ctx);
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            clientLogger.log(level, "msg received on client " + msg + " class=" + msg.getClass());
            if (msg instanceof FullHttpResponse) {
                completableFuture.complete(true);
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
            serverLogger.log(level, "got event on server " + evt);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            serverLogger.log(level, "msg received on server " + msg + " class=" + msg.getClass());
            if (msg instanceof FullHttpRequest) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK);
                serverLogger.log(Level.INFO, "writing server response: " + response);
                ctx.writeAndFlush(response);
            }
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
