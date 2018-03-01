package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.HttpAddress;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final Http2SettingsHandler http2SettingsHandler;

    private final Http2ResponseHandler http2ResponseHandler;

    public Http2ChannelInitializer(ClientConfig clientConfig,
                            HttpAddress httpAddress,
                            Http2SettingsHandler http2SettingsHandler,
                            Http2ResponseHandler http2ResponseHandler) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.http2SettingsHandler = http2SettingsHandler;
        this.http2ResponseHandler = http2ResponseHandler;
    }

    /**
     * The channel initialization for HTTP/2 is always encrypted.
     * The reason is there is no known HTTP/2 server supporting cleartext.
     *
     * @param ch socket channel
     */
    @Override
    protected void initChannel(SocketChannel ch) {
        DefaultHttp2Connection http2Connection = new DefaultHttp2Connection(false);
        HttpToHttp2ConnectionHandlerBuilder http2ConnectionHandlerBuilder = new HttpToHttp2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                        new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                .maxContentLength(clientConfig.getMaxContentLength())
                                .propagateSettings(true)
                                .build()));
        if (clientConfig.isDebug()) {
            http2ConnectionHandlerBuilder.frameLogger(new Http2FrameLogger(LogLevel.INFO, "client"));
        }
        Http2ConnectionHandler http2ConnectionHandler = http2ConnectionHandlerBuilder.build();
        try {
            SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                    .sslProvider(clientConfig.getSslProvider())
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2));
            if (clientConfig.getSslContextProvider() != null) {
                sslContextBuilder.sslContextProvider(clientConfig.getSslContextProvider());
            }
            SslContext sslContext = sslContextBuilder.build();
            SslHandler sslHandler = sslContext.newHandler(ch.alloc());
            SSLEngine engine = sslHandler.engine();
            if (clientConfig.isServerNameIdentification()) {
                String fullQualifiedHostname = httpAddress.getInetSocketAddress().getHostName();
                SSLParameters params = engine.getSSLParameters();
                params.setServerNames(Collections.singletonList(new SNIHostName(fullQualifiedHostname)));
                engine.setSSLParameters(params);
            }
            ch.pipeline().addLast(sslHandler);
            ApplicationProtocolNegotiationHandler negotiationHandler = new ApplicationProtocolNegotiationHandler("") {
                @Override
                protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                    if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                        ctx.pipeline().addLast(http2ConnectionHandler, http2SettingsHandler, http2ResponseHandler);
                        return;
                    }
                    ctx.close();
                    throw new IllegalStateException("unknown protocol: " + protocol);
                }
            };
            ch.pipeline().addLast(negotiationHandler);
        } catch (SSLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }
}
