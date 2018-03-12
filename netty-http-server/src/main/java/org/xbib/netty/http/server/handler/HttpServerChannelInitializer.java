package org.xbib.netty.http.server.handler;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;
import io.netty.util.DomainNameMapping;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;
import org.xbib.netty.http.server.handler.http1.HttpHandler;
import org.xbib.netty.http.server.handler.http1.IdleTimeoutHandler;
import org.xbib.netty.http.server.handler.http1.TrafficLoggingHandler;
import org.xbib.netty.http.server.handler.http2.UserEventLogger;
import org.xbib.netty.http.server.internal.Http1ObjectEncoder;
import org.xbib.netty.http.server.internal.Http2ObjectEncoder;
import org.xbib.netty.http.server.internal.HttpObjectEncoder;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server channel initializer.
 */
public class HttpServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    private static final Logger logger = Logger.getLogger(HttpServerChannelInitializer.class.getName());

    private final Server server;

    private final ServerConfig serverConfig;

    private final Http2ConnectionHandler http2ConnectionHandler;

    private final DomainNameMapping<SslContext> domainNameMapping;

    public HttpServerChannelInitializer(Server server, ServerConfig serverConfig,
                                        DomainNameMapping<SslContext> domainNameMapping) {
        this.server = server;
        this.serverConfig = serverConfig;
        this.domainNameMapping = domainNameMapping;
        this.http2ConnectionHandler = null;//createHttp2ConnectionHandler(serverConfig);

    }

    @Override
    public void initChannel(SocketChannel ch) {
        if (serverConfig.isDebug()) {
            ch.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (serverConfig.getAddress().isSecure()) {
            configureSecure(ch);
        } else {
            configureClearText(ch);
        }
        HttpObjectEncoder encoder = serverConfig.getAddress().getVersion().majorVersion() == 2 ?
                new Http2ObjectEncoder(http2ConnectionHandler.encoder()) :
                new Http1ObjectEncoder();
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, "server channel initialized: " + ch.pipeline().names());
        }
    }

    private void configureClearText(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        if (serverConfig.getAddress().getVersion().majorVersion() == 1) {
            if (serverConfig.isInstallHttp2Upgrade()) {
                installHttp2Upgrade(pipeline);
            } else {
                pipeline.addFirst(new IdleTimeoutHandler());
                pipeline.addLast(new UserEventLogger());
                pipeline.addLast(createHttp1ConnectionHandler(serverConfig));
                configureHttp1Pipeline(pipeline);
            }
        } else if (serverConfig.getAddress().getVersion().majorVersion() == 2) {
            pipeline.addLast(http2ConnectionHandler);
            configureHttp2Pipeline(pipeline);
        }
    }

    private void installHttp2Upgrade(ChannelPipeline pipeline) {
        HttpServerCodec httpServerCodec = new HttpServerCodec();
        HttpServerUpgradeHandler httpServerUpgradeHandler = new HttpServerUpgradeHandler(httpServerCodec, protocol -> {
            if (AsciiString.contentEquals("h2c", protocol)) {
                return new Http2ServerUpgradeCodec(http2ConnectionHandler);
            } else {
                return null;
            }
        });
        pipeline.addLast(new CleartextHttp2ServerUpgradeHandler(httpServerCodec, httpServerUpgradeHandler,
                new HttpHandler(server)));
    }

    private void configureSecure(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new SniHandler(domainNameMapping));
        pipeline.addLast(new Http2NegotiationHandler(ApplicationProtocolNames.HTTP_1_1));
    }

    private HttpServerCodec createHttp1ConnectionHandler(ServerConfig context) {
        return new HttpServerCodec(context.getMaxInitialLineLength(),
                context.getMaxHeadersSize(), context.getMaxChunkSize());
    }

    private void configureHttp1Pipeline(ChannelPipeline pipeline) {
        if (serverConfig.isEnableGzip()) {
            pipeline.addLast(new HttpContentDecompressor());
        }
        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(serverConfig.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(serverConfig.getMaxCompositeBufferComponents());
        pipeline.addLast(httpObjectAggregator);
        pipeline.addLast(new HttpHandler(server));
    }

    private void configureHttp2Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast(new UserEventLogger());
        pipeline.addLast(new HttpHandler(server));
    }

    /*private static Http2ConnectionHandler createHttp2ConnectionHandler(ServerConfig serverConfig) {
        Http2Settings initialSettings = serverConfig.getHttp2Settings();
        Http2Connection http2Connection = new DefaultHttp2Connection(true);
        Long maxHeaderListSize = initialSettings.maxHeaderListSize();
        Http2FrameReader frameReader = new DefaultHttp2FrameReader(maxHeaderListSize == null ?
                new DefaultHttp2HeadersDecoder(true) :
                new DefaultHttp2HeadersDecoder(true, maxHeaderListSize));
        Http2FrameWriter frameWriter = new DefaultHttp2FrameWriter();
        Http2FrameLogger frameLogger = null;
        if (serverConfig.isDebug()) {
            frameLogger = new Http2FrameLogger(serverConfig.getDebugLogLevel(), "server");
        }
        if (frameLogger != null) {
            frameWriter = new Http2OutboundFrameLogger(frameWriter, frameLogger);
            frameReader = new Http2InboundFrameLogger(frameReader, frameLogger);
        }
        Http2ConnectionEncoder encoder = new DefaultHttp2ConnectionEncoder(http2Connection, frameWriter);
        Http2ConnectionDecoder decoder = new DefaultHttp2ConnectionDecoder(http2Connection, encoder, frameReader);
        Http2ConnectionHandler http2ConnectionHandler = new Http2ServerConnectionHandler(decoder, encoder, initialSettings);
        Http2Handler http2Handler = new Http2Handler(serverConfig, http2Connection, true);
        http2ConnectionHandler.connection().addListener(http2Handler);
        http2ConnectionHandler.decoder().frameListener(new DelegatingDecompressorFrameListener(http2Connection, http2Handler));
        if (serverConfig.getIdleTimeoutMillis() > 0) {
            http2ConnectionHandler.gracefulShutdownTimeoutMillis(serverConfig.getIdleTimeoutMillis());
        }
        return http2ConnectionHandler;
    }*/

    private ChannelHandler createMultiplexInitializer() {
        /*HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(serverConfig.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(serverConfig.getMaxCompositeBufferComponents());*/
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(http2ConnectionHandler);
                configureHttp2Pipeline(ch.pipeline());
                        //.addLast(new Http2StreamFrameToHttpObjectCodec(true))
                        //.addLast(httpObjectAggregator)
                        //.addLast(httpHandler);
            }
        };
    }

    private Http2MultiplexCodec createHttp2MultiplexCodec() {
        Http2MultiplexCodecBuilder multiplexCodecBuilder = Http2MultiplexCodecBuilder.forServer(createMultiplexInitializer());
        multiplexCodecBuilder.initialSettings(serverConfig.getHttp2Settings());
        if (serverConfig.getIdleTimeoutMillis() > 0) {
            multiplexCodecBuilder.gracefulShutdownTimeoutMillis(serverConfig.getIdleTimeoutMillis());
        }
        return multiplexCodecBuilder.build();
    }

    /**
     * Negotiates with the browser if HTTP/2 or HTTP is going to be used. Once decided, the
     * pipeline is setup with the correct handlers for the selected protocol.
     */
    class Http2NegotiationHandler extends ApplicationProtocolNegotiationHandler {

        Http2NegotiationHandler(String fallbackProtocol) {
            super(fallbackProtocol);
        }

        @Override
        protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
            ChannelPipeline pipeline = ctx.pipeline();
            if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
                pipeline.addLast(createHttp1ConnectionHandler(serverConfig));
                configureHttp1Pipeline(pipeline);
                return;
            }
            if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
                pipeline.addLast(http2ConnectionHandler);
                configureHttp2Pipeline(pipeline);
                if (serverConfig.isDebug()) {
                    logger.log(Level.INFO, "after successful HTTP/2 negotiation: " + pipeline.names());
                }
                return;
            }
            ctx.close();
            throw new IllegalStateException("unknown protocol: " + protocol);
        }
    }
}
