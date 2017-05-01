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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
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
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;

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
 */
class HttpClientChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(HttpClientChannelInitializer.class.getName());

    private static final Http2FrameLogger frameLogger =
            new Http2FrameLogger(LogLevel.TRACE, HttpClientChannelInitializer.class);

    private final HttpClientChannelContext context;

    private final Http1Handler http1Handler;

    private final Http2Handler http2Handler;

    private InetAddressKey key;

    private Http2SettingsHandler http2SettingsHandler;

    private UserEventLogger userEventLogger;

    HttpClientChannelInitializer(HttpClientChannelContext context, Http1Handler http1Handler,
                                 Http2Handler http2Handler) {
        this.context = context;
        this.http1Handler = http1Handler;
        this.http2Handler = http2Handler;
    }

    void initChannel(SocketChannel ch, InetAddressKey key) throws Exception {
        this.key = key;
        initChannel(ch);
    }

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        logger.log(Level.FINE, () -> "initChannel with key = " + key);
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
        http2SettingsHandler = new Http2SettingsHandler(ch.newPromise());
        userEventLogger = new UserEventLogger();
        if (context.getSslProvider() != null && key.isSecure()) {
            configureEncrypted(ch);
        } else {
           configureClearText(ch);
        }
        logger.log(Level.FINE, () -> "initChannel pipeline handler names = " + ch.pipeline().names());
    }

    Http2SettingsHandler getHttp2SettingsHandler() {
        return http2SettingsHandler;
    }

    Http2Handler getHttp2Handler() {
        return http2Handler;
    }

    private void configureClearText(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (key.getVersion().majorVersion() == 1) {
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline);
        } else if (key.getVersion().majorVersion() == 2) {
            HttpToHttp2ConnectionHandler http2connectionHandler = createHttp2ConnectionHandler();
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
        if (key.getVersion().majorVersion() == 2) {
            final SslContext http2SslContext = SslContextBuilder.forClient()
                    .sslProvider(context.getSslProvider())
                    .keyManager(context.getKeyCertChainInputStream(), context.getKeyInputStream(), context.getKeyPassword())
                    .ciphers(context.getCiphers(), context.getCipherSuiteFilter())
                    .trustManager(context.getTrustManagerFactory())
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1))
                    .build();
            SslHandler sslHandler = http2SslContext.newHandler(ch.alloc());
            try {
                SSLEngine engine = sslHandler.engine();
                if (context.isUseServerNameIdentification()) {
                    // execute DNS lookup and/or reverse lookup if IP for host name
                    String fullQualifiedHostname = key.getInetSocketAddress().getHostName();
                    SSLParameters params = engine.getSSLParameters();
                    params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                    engine.setSSLParameters(params);
                }
                switch (context.getSslClientAuthMode()) {
                    case NEED:
                        engine.setNeedClientAuth(true);
                        break;
                    case WANT:
                        engine.setWantClientAuth(true);
                        break;
                    default:
                        break;
                }
            } finally {
                pipeline.addLast(sslHandler);
            }
            pipeline.addLast(new Http2NegotiationHandler(ApplicationProtocolNames.HTTP_1_1));
        } else if (key.getVersion().majorVersion() == 1) {
            final SslContext hhtp1SslContext = SslContextBuilder.forClient()
                    .sslProvider(context.getSslProvider())
                    .keyManager(context.getKeyCertChainInputStream(), context.getKeyInputStream(), context.getKeyPassword())
                    .ciphers(context.getCiphers(), context.getCipherSuiteFilter())
                    .trustManager(context.getTrustManagerFactory())
                    .build();
            SslHandler sslHandler = hhtp1SslContext.newHandler(ch.alloc());
            switch (context.getSslClientAuthMode()) {
                case NEED:
                    sslHandler.engine().setNeedClientAuth(true);
                    break;
                case WANT:
                    sslHandler.engine().setWantClientAuth(true);
                    break;
                default:
                    break;
            }
            pipeline.addLast(sslHandler);
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline);
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
        pipeline.addLast(http1Handler);
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(http2SettingsHandler);
        pipeline.addLast(userEventLogger);
        pipeline.addLast(http2Handler);
    }

    private HttpClientCodec createHttp1ConnectionHandler() {
        return new HttpClientCodec(context.getMaxInitialLineLength(), context.getMaxHeaderSize(), context.getMaxChunkSize());
    }

    private HttpToHttp2ConnectionHandler createHttp2ConnectionHandler() {
        final Http2Connection http2Connection = new DefaultHttp2Connection(false);
        return new HttpToHttp2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameLogger(frameLogger)
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                        new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                .maxContentLength(context.getMaxContentLength())
                                .propagateSettings(true)
                                .validateHttpHeaders(false)
                                .build()))
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
                logger.log(Level.FINE, "negotiated HTTP/2: handler = " + ctx.pipeline().names());
                return;
            }
            if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler();
                ctx.pipeline().addLast(http1connectionHandler);
                configureHttp1Pipeline(ctx.pipeline());
                logger.log(Level.FINE, "negotiated HTTP/1.1: handler = " + ctx.pipeline().names());
                return;
            }
            ctx.close();
            throw new IllegalStateException("unexpected protocol: " + protocol);
        }
    }

    class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {

        private final ChannelPromise promise;

        Http2SettingsHandler(ChannelPromise promise) {
            this.promise = promise;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings msg) throws Exception {
            promise.setSuccess();
            ctx.pipeline().remove(this);
            logger.log(Level.FINE, "settings handler removed, pipeline = " + ctx.pipeline().names());
        }

        /**
         * Forward channel exceptions to the exception listener.
         * @param ctx the channel handler context
         * @param cause the cause of the exception
         * @throws Exception if forwarding fails
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ExceptionListener exceptionListener =
                    ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
            logger.log(Level.FINE, () -> "exceptionCaught");
            if (exceptionListener != null) {
                exceptionListener.onException(cause);
            }
            final HttpRequestContext httpRequestContext =
                    ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
            httpRequestContext.fail(cause.getMessage());
        }

        void awaitSettings(HttpRequestContext httpRequestContext, ExceptionListener exceptionListener) throws Exception {
            int timeout = httpRequestContext.getTimeout();
            if (!promise.awaitUninterruptibly(timeout, TimeUnit.MILLISECONDS)) {
                IllegalStateException exception = new IllegalStateException("time out while waiting for HTTP/2 settings");
                if (exceptionListener != null) {
                    exceptionListener.onException(exception);
                    httpRequestContext.fail(exception.getMessage());
                }
                throw exception;
            }
            if (!promise.isSuccess()) {
                throw new RuntimeException(promise.cause());
            }
        }
    }

    @Sharable
    private class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            DefaultFullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
            ctx.writeAndFlush(upgradeRequest);
            super.channelActive(ctx);
            ctx.pipeline().remove(this);
            logger.log(Level.FINE, "upgrade request handler removed, pipeline = " + ctx.pipeline().names());
        }

        /**
         * Forward channel exceptions to the exception listener.
         * @param ctx the channel handler context
         * @param cause the cause of the exception
         * @throws Exception if forwarding fails
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            ExceptionListener exceptionListener =
                    ctx.channel().attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
            logger.log(Level.FINE, () -> "exceptionCaught");
            if (exceptionListener != null) {
                exceptionListener.onException(cause);
            }
            final HttpRequestContext httpRequestContext =
                    ctx.channel().attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
            httpRequestContext.fail(cause.getMessage());
        }
    }

    @Sharable
    private class UserEventLogger extends ChannelInboundHandlerAdapter {

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
}
