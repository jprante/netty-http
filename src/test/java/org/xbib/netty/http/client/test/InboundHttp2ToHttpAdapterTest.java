package org.xbib.netty.http.client.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceWrittenEvent;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.internal.PlatformDependent;
import org.junit.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class InboundHttp2ToHttpAdapterTest {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %2$s %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(Level.ALL);
        }
    }

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testInboundHttp2ToHttpAdapter() throws Exception {
        URL url = new URL("https://http2-push.io");
        final InetSocketAddress inetSocketAddress = new InetSocketAddress(url.getHost(), 443);
        EventLoopGroup group = new NioEventLoopGroup();
        Channel clientChannel = null;
        SettingsHandler settingsHandler = new SettingsHandler();
        ResponseHandler responseHandler = new ResponseHandler();
        try {
            Bootstrap bs = new Bootstrap();
            bs.group(group);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new TrafficLoggingHandler());
                    SslContext sslContext = SslContextBuilder.forClient()
                            .sslProvider(SslProvider.OPENSSL)
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            .applicationProtocolConfig(new ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2))
                            .build();
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                    SSLEngine engine = sslHandler.engine();
                    String fullQualifiedHostname = inetSocketAddress.getHostName();
                    SSLParameters params = engine.getSSLParameters();
                    params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                    engine.setSSLParameters(params);
                    ch.pipeline().addLast(sslHandler);
                    ch.pipeline().addLast(new Http2NegotiationHandler(settingsHandler, responseHandler));
                }
            });
            clientChannel = bs.connect(inetSocketAddress).syncUninterruptibly().channel();
            settingsHandler.awaitSettings(clientChannel.newPromise());
            HttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.valueOf("HTTP/2.0"),
                    HttpMethod.GET, url.toExternalForm());
            logger.log(Level.FINE, "HTTP2: sending request");
            responseHandler.put(3, clientChannel.write(httpRequest), clientChannel.newPromise());
            clientChannel.flush();
            logger.log(Level.FINE, "HTTP2: waiting for responses");
            responseHandler.awaitResponses();
            logger.log(Level.FINE, "HTTP2: done");
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            group.shutdownGracefully();
        }
    }

    private HttpToHttp2ConnectionHandler createHttp2ConnectionHandler() {
        final Http2Connection http2Connection = new DefaultHttp2Connection(false);
        return new HttpToHttp2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameLogger(new Http2FrameLogger(LogLevel.INFO, "client"))
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                        new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                .maxContentLength(10 * 1024 * 1024)
                                .propagateSettings(true)
                                .validateHttpHeaders(false)
                                .build()))
                .build();
    }

    class Http2NegotiationHandler extends ApplicationProtocolNegotiationHandler {

        private final SettingsHandler settingsHandler;

        private final ResponseHandler responseHandler;

        Http2NegotiationHandler(SettingsHandler settingsHandler, ResponseHandler responseHandler) {
            super("");
            this.settingsHandler = settingsHandler;
            this.responseHandler = responseHandler;
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                ctx.pipeline().addLast(createHttp2ConnectionHandler());
                ctx.pipeline().addLast(settingsHandler);
                ctx.pipeline().addLast(new UserEventLogger());
                ctx.pipeline().addLast(responseHandler);
                logger.log(Level.FINE, "negotiated HTTP/2: pipeline = " + ctx.pipeline().names());
                return;
            }
            ctx.close();
            throw new IllegalStateException("unexpected protocol: " + protocol);
        }
    }

    class SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {

        private ChannelPromise promise;

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) throws Exception {
            promise.setSuccess();
            ctx.pipeline().remove(this);
        }

        void awaitSettings(ChannelPromise promise) throws Exception {
            this.promise = promise;
            int timeout = 5000;
            if (!promise.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("time out while waiting for HTTP/2 settings");
            }
            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }
        }
    }

    class ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        private final Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> streamidPromiseMap;

        ResponseHandler() {
            this.streamidPromiseMap = PlatformDependent.newConcurrentHashMap();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse httpResponse) throws Exception {
            Integer streamId = httpResponse.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
            if (streamId == null) {
                logger.log(Level.WARNING, () -> "stream ID missing");
                return;
            }
            Map.Entry<ChannelFuture, ChannelPromise> entry = streamidPromiseMap.get(streamId);
            if (entry != null) {
                entry.getValue().setSuccess();
            } else {
                logger.log(Level.WARNING, () -> "stream id not found in promise map: " + streamId);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.log(Level.FINE, () -> "exception caught " + cause.getMessage());
        }

        void put(int streamId, ChannelFuture channelFuture, ChannelPromise promise) {
            logger.log(Level.FINE, () -> "put stream ID " + streamId);
            streamidPromiseMap.put(streamId, new AbstractMap.SimpleEntry<>(channelFuture, promise));
        }

        void awaitResponses() {
            int timeout = 5000;
            Iterator<Map.Entry<Integer, Map.Entry<ChannelFuture, ChannelPromise>>> iterator = streamidPromiseMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Map.Entry<ChannelFuture, ChannelPromise>> entry = iterator.next();
                ChannelFuture channelFuture = entry.getValue().getKey();
                if (!channelFuture.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("time out while waiting to write for stream id " + entry.getKey());
                }
                if (!channelFuture.isSuccess()) {
                    throw new RuntimeException(channelFuture.cause());
                }
                ChannelPromise promise = entry.getValue().getValue();
                if (!promise.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("time out while waiting for response on stream id " + entry.getKey());
                }
                if (!promise.isSuccess()) {
                    throw new RuntimeException(promise.cause());
                }
                iterator.remove();
            }
        }
    }

    class UserEventLogger extends ChannelInboundHandlerAdapter {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            logger.log(Level.FINE, () -> "got user event " + evt);
            if (evt instanceof Http2ConnectionPrefaceWrittenEvent ||
                    evt instanceof SslCloseCompletionEvent ||
                    evt instanceof ChannelInputShutdownReadComplete) {
                // Expected events
                logger.log(Level.FINE, () -> "user event is expected: " + evt);
                return;
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    class TrafficLoggingHandler extends LoggingHandler {

        TrafficLoggingHandler() {
            super("client", LogLevel.TRACE);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
            ctx.fireChannelUnregistered();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) throws Exception {
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
