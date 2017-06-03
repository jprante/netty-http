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
package org.xbib.netty.http.client.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.xbib.netty.http.client.HttpClientChannelContext;
import org.xbib.netty.http.client.util.InetAddressKey;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty HTTP client channel initializer.
 */
public class HttpClientChannelInitializer extends ChannelInitializer<SocketChannel> {

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
    public HttpClientChannelInitializer(HttpClientChannelContext context, HttpHandler httpHandler,
                                 Http2ResponseHandler http2ResponseHandler) {
        this.context = context;
        this.httpHandler = httpHandler;
        this.http2ResponseHandler = http2ResponseHandler;
    }

    HttpClientChannelContext getContext() {
        return context;
    }

    HttpHandler getHttpHandler() {
        return httpHandler;
    }

    Http2ResponseHandler getHttp2ResponseHandler() {
        return http2ResponseHandler;
    }

    /**
     * Sets up a {@link InetAddressKey} for the channel initialization and initializes the channel.
     * Using this method, the channel initializer can handle secure channels, the HTTP protocol version,
     * and the host name for Server Name Identification (SNI).
     * @param ch the channel
     * @param key the key of the internet address
     * @throws Exception if channel
     */
    public void initChannel(SocketChannel ch, InetAddressKey key) throws Exception {
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
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler(context);
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline, context, httpHandler);
        } else if (key.getVersion().majorVersion() == 2) {
            Http2ConnectionHandler http2connectionHandler = createHttp2ConnectionHandler(context);
            // using the upgrade handler means mixed HTTP 1 and HTTP 2 on the same connection
            if (context.isInstallHttp2Upgrade()) {
                HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler(context);
                Http2ClientUpgradeCodec upgradeCodec =
                        new Http2ClientUpgradeCodec(http2connectionHandler);
                HttpClientUpgradeHandler upgradeHandler =
                        new HttpClientUpgradeHandler(http1connectionHandler, upgradeCodec, context.getMaxContentLength());
                pipeline.addLast(upgradeHandler);
                UpgradeRequestHandler upgradeRequestHandler =
                        new UpgradeRequestHandler();
                pipeline.addLast(upgradeRequestHandler);
            } else {
                pipeline.addLast(http2connectionHandler);
            }
            configureHttp2Pipeline(pipeline, http2ResponseHandler);
            configureHttp1Pipeline(pipeline, context, httpHandler);
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
            HttpClientCodec http1connectionHandler = createHttp1ConnectionHandler(context);
            pipeline.addLast(http1connectionHandler);
            configureHttp1Pipeline(pipeline, context, httpHandler);
        } else if (key.getVersion().majorVersion() == 2) {
            pipeline.addLast(new Http2NegotiationHandler(ApplicationProtocolNames.HTTP_1_1, this));
        }
    }

    static void configureHttp1Pipeline(ChannelPipeline pipeline, HttpClientChannelContext context, HttpHandler httpHandler) {
        if (context.isGzipEnabled()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(context.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(context.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        pipeline.addLast(httpHandler);
    }

    static void configureHttp2Pipeline(ChannelPipeline pipeline, Http2ResponseHandler http2ResponseHandler) {
        pipeline.addLast(new UserEventLogger());
        pipeline.addLast(http2ResponseHandler);
    }

    static HttpClientCodec createHttp1ConnectionHandler(HttpClientChannelContext context) {
        return new HttpClientCodec(context.getMaxInitialLineLength(), context.getMaxHeaderSize(), context.getMaxChunkSize());
    }

    static Http2ConnectionHandler createHttp2ConnectionHandler(HttpClientChannelContext context) {
        final Http2Connection http2Connection = new DefaultHttp2Connection(false);
        return new Http2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameLogger(new Http2FrameLogger(LogLevel.TRACE, HttpClientChannelInitializer.class))
                .initialSettings(new Http2Settings())
                .encoderEnforceMaxConcurrentStreams(true)
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                        new Http2EventHandler(http2Connection, context.getMaxContentLength(), false)))
                .build();
    }
}
