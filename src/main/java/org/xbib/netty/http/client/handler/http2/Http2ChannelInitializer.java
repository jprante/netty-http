package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.HttpAddress;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final SslHandler sslHandler;

    private final Http2SettingsHandler http2SettingsHandler;

    private final Http2ResponseHandler http2ResponseHandler;

    public Http2ChannelInitializer(ClientConfig clientConfig,
                            HttpAddress httpAddress,
                            SslHandler sslHandler,
                            Http2SettingsHandler http2SettingsHandler,
                            Http2ResponseHandler http2ResponseHandler) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.sslHandler = sslHandler;
        this.http2SettingsHandler = http2SettingsHandler;
        this.http2ResponseHandler = http2ResponseHandler;
    }

    /**
     * The channel initialization for HTTP/2.
     *
     * @param channel socket channel
     */
    @Override
    public void initChannel(SocketChannel channel) {
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (clientConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP/2 channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(SocketChannel channel) {
        channel.pipeline().addLast(sslHandler);
        ApplicationProtocolNegotiationHandler negotiationHandler = new ApplicationProtocolNegotiationHandler("") {
            @Override
            protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
                if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                    ctx.pipeline().addLast(newConnectionHandler(), http2SettingsHandler, http2ResponseHandler);
                    if (clientConfig.isDebug()) {
                        logger.log(Level.FINE, "after negotiation: " + ctx.pipeline().names());
                    }
                    return;
                }
                // we do not fall back to HTTP1
                ctx.close();
                throw new IllegalStateException("protocol not accepted: " + protocol);
            }
        };
        channel.pipeline().addLast(negotiationHandler);
}

    private void configureCleartext(SocketChannel ch) {
        ch.pipeline().addLast(newConnectionHandler(), http2SettingsHandler, http2ResponseHandler);
    }

    private Http2ConnectionHandler newConnectionHandler() {
        Http2Connection http2Connection = new DefaultHttp2Connection(false);
        HttpToHttp2ConnectionHandlerBuilder http2ConnectionHandlerBuilder = new HttpToHttp2ConnectionHandlerBuilder()
                .initialSettings(clientConfig.getHttp2Settings())
                .connection(http2Connection)
                .frameListener(new Http2PushPromiseHandler(http2Connection,
                        new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                .maxContentLength(clientConfig.getMaxContentLength())
                                .propagateSettings(true)
                                .build()));
        if (clientConfig.isDebug()) {
            http2ConnectionHandlerBuilder.frameLogger(new Http2FrameLogger(clientConfig.getDebugLogLevel(), "client"));
        }
        return http2ConnectionHandlerBuilder.build();
    }
}
