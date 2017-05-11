/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceWrittenEvent;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.util.InetAddressKey;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;

/**
 * Netty HTTP client channel initializer.
 */
class HttpClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(HttpClientChannelInitializer.class.getName());

    private final HttpClientChannelContext context;

    private final HttpHandler httpHandler;

    private final Http2ResponseHandler http2ResponseHandler;

    private InetAddressKey key;

    /**
     * Constructor for a new {@link HttpClientChannelInitializer}.
     * @param context the HTTP client channel context
     * @param httpHandler the HTTP 1.x handler
     * @param http2ResponseHandler the HTTP 2 handler
     */
    HttpClientChannelInitializer(HttpClientChannelContext context, HttpHandler httpHandler,
                                 Http2ResponseHandler http2ResponseHandler) {
        this.context = context;
        this.httpHandler = httpHandler;
        this.http2ResponseHandler = http2ResponseHandler;
    }

    /**
     * Sets up a {@link InetAddressKey} for the channel initialization and initializes the channel.
     * Using this method, the channel initializer can handle secure channels, the HTTP protocol version,
     * and the host name for Server Name Identification (SNI).
     * @param ch the channel
     * @param key the key of the internet address
     * @throws Exception if channel
     */
    void initChannel(SocketChannel ch, InetAddressKey key) throws Exception {
        this.key = key;
        initChannel(ch);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        logger.log(Level.FINE, () -> "initChannel with key = " + key);
        if (key == null) {
            throw new IllegalStateException("no key set for channel initialization");
        }
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new TrafficLoggingHandler());
        if (context.getHttpProxyHandler() != null) {
            pipeline.addLast(context.getHttpProxyHandler());
        }
        if (context.getSocks4ProxyHandler() != null) {
            pipeline.addLast(context.getSocks4ProxyHandler());
        }
        if (context.getSocks5ProxyHandler() != null) {
            pipeline.addLast(context.getSocks5ProxyHandler());
        }
        pipeline.addLast(new ReadTimeoutHandler(context.getReadTimeoutMillis(), TimeUnit.MILLISECONDS));
        if (context.getSslProvider() != null && key.isSecure()) {
            configureEncrypted(ch);
        } else {
           configureClearText(ch);
        }
        logger.log(Level.FINE, () -> "initChannel complete, pipeline handler names = " + ch.pipeline().names());
    }

    private void configureClearText(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (key.getVersion().majorVersion() == 1) {
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline);
        } else if (key.getVersion().majorVersion() == 2) {
            HttpToHttp2ConnectionHandler http2connectionHandler = createHttp2ConnectionHandler();
            // using the upgrade handler means mixed HTTP 1 and HTTP 2 on the same connection
            if (context.isInstallHttp2Upgrade()) {
                HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
                Http2ClientUpgradeCodec upgradeCodec =
                        new Http2ClientUpgradeCodec(http2connectionHandler);
                HttpClientUpgradeHandler upgradeHandler =
                        new HttpClientUpgradeHandler(http1connectionHandler, upgradeCodec, context.getMaxContentLength());
                UpgradeRequestHandler upgradeRequestHandler =
                        new UpgradeRequestHandler();
                pipeline.addLast(upgradeHandler);
                pipeline.addLast(upgradeRequestHandler);
            } else {
                pipeline.addLast(http2connectionHandler);
            }
            configureHttp2Pipeline(pipeline);
            configureHttp1Pipeline(pipeline);
        }
    }

    private void configureEncrypted(SocketChannel ch) throws SSLException {
        ChannelPipeline pipeline = ch.pipeline();
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                .sslProvider(context.getSslProvider())
                .keyManager(context.getKeyCertChainInputStream(), context.getKeyInputStream(), context.getKeyPassword())
                .ciphers(context.getCiphers(), context.getCipherSuiteFilter())
                .trustManager(context.getTrustManagerFactory());
        if (key.getVersion().majorVersion() == 2) {
            sslContextBuilder.applicationProtocolConfig(new ApplicationProtocolConfig(
                    ApplicationProtocolConfig.Protocol.ALPN,
                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                    ApplicationProtocolNames.HTTP_2,
                    ApplicationProtocolNames.HTTP_1_1));
        }
        SslHandler sslHandler = sslContextBuilder.build().newHandler(ch.alloc());
        SSLEngine engine = sslHandler.engine();
        try {
            if (context.isUseServerNameIdentification()) {
                String fullQualifiedHostname = key.getInetSocketAddress().getHostName();
                SSLParameters params = engine.getSSLParameters();
                params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                engine.setSSLParameters(params);
            }
        } finally {
            pipeline.addLast(sslHandler);
        }
        switch (context.getClientAuthMode()) {
            case NEED:
                engine.setNeedClientAuth(true);
                break;
            case WANT:
                engine.setWantClientAuth(true);
                break;
            default:
                break;
        }
        if (key.getVersion().majorVersion() == 1) {
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline);
        } else if (key.getVersion().majorVersion() == 2) {
            pipeline.addLast(new Http2NegotiationHandler(ApplicationProtocolNames.HTTP_1_1));
        }
    }

    private void configureHttp1Pipeline(ChannelPipeline pipeline) {
        if (context.isGzipEnabled()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(context.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(context.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        pipeline.addLast(httpHandler);
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new UserEventLogger());
        pipeline.addLast(http2ResponseHandler);
    }

    private HttpClientCodec createHttp1ConnectionHandler() {
        return new HttpClientCodec(context.getMaxInitialLineLength(), context.getMaxHeaderSize(), context.getMaxChunkSize());
    }

    private HttpToHttp2ConnectionHandler createHttp2ConnectionHandler() {
        final Http2Connection http2Connection = new DefaultHttp2Connection(false);
        return new HttpToHttp2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameLogger(new Http2FrameLogger(LogLevel.TRACE, HttpClientChannelInitializer.class))
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                        new Http2EventHandler(http2Connection, context.getMaxContentLength(), false)))
                .build();
    }

    private class Http2NegotiationHandler extends ApplicationProtocolNegotiationHandler {

        Http2NegotiationHandler(String fallbackProtocol) {
            super(fallbackProtocol);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) throws Exception {
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                HttpToHttp2ConnectionHandler http2connectionHandler = createHttp2ConnectionHandler();
                ctx.pipeline().addLast(http2connectionHandler);
                configureHttp2Pipeline(ctx.pipeline());
                logger.log(Level.FINE, () -> "negotiated HTTP/2: handler = " + ctx.pipeline().names());
                return;
            }
            if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
                ctx.pipeline().addLast(http1connectionHandler);
                configureHttp1Pipeline(ctx.pipeline());
                logger.log(Level.FINE, () -> "negotiated HTTP/1.1: handler = " + ctx.pipeline().names());
                return;
            }
            // close and fail
            ctx.close();
            throw new IllegalStateException("unexpected protocol: " + protocol);
        }
    }

    @Sharable
    private class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

        /**
         * Send an upgrade request if channel becomes active.
         * @param ctx the channel handler context
         * @throws Exception if upgrade request sending fails
         */
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            DefaultFullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            ctx.writeAndFlush(upgradeRequest);
            super.channelActive(ctx);
            ctx.pipeline().remove(this);
            logger.log(Level.FINE, () -> "upgrade request handler removed, pipeline = " + ctx.pipeline().names());
        }

        /**
         * Forward channel exceptions to the exception listener.
         * @param ctx the channel handler context
         * @param cause the cause of the exception
         * @throws Exception if forwarding fails
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.log(Level.FINE, () -> "exceptionCaught " + cause.getMessage());
            ExceptionListener exceptionListener =
                    ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
            if (exceptionListener != null) {
                exceptionListener.onException(cause);
            }
            final HttpRequestContext httpRequestContext =
                    ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
            httpRequestContext.fail(cause.getMessage());
        }
    }

    /**
     * A Netty handler that logs user events and find expetced ones.
     */
    @Sharable
    private class UserEventLogger extends ChannelInboundHandlerAdapter {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            logger.log(Level.FINE, () -> "got user event " + evt);
            if (evt instanceof Http2ConnectionPrefaceWrittenEvent ||
                    evt instanceof SslCloseCompletionEvent ||
                    evt instanceof ChannelInputShutdownReadComplete) {
                // log expected events
                logger.log(Level.FINE, () -> "user event is expected: " + evt);
                return;
            }
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * A Netty handler that logs the I/O traffic of a connection.
     */
    @Sharable
    private final class TrafficLoggingHandler extends LoggingHandler {

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
