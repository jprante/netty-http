package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.CleartextHttp2ServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AsciiString;
import io.netty.util.DomainNameMapping;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;
import org.xbib.netty.http.server.handler.TrafficLoggingHandler;
import org.xbib.netty.http.server.transport.ServerTransport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<Channel> {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final Server server;

    private final ServerConfig serverConfig;

    private final HttpAddress httpAddress;

    private final SniHandler sniHandler;

    public Http2ChannelInitializer(Server server,
                                   HttpAddress httpAddress,
                                   DomainNameMapping<SslContext> domainNameMapping) {
        this.server = server;
        this.serverConfig = server.getServerConfig();
        this.httpAddress = httpAddress;
        this.sniHandler = domainNameMapping != null ? new SniHandler(domainNameMapping) : null;
    }

    @Override
    public void initChannel(Channel channel) {
        ServerTransport serverTransport = server.newTransport(httpAddress.getVersion());
        channel.attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).set(serverTransport);
        if (serverConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (server.getServerConfig().isDebug()) {
            logger.log(Level.FINE, "HTTP/2 server channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel) {
        if (sniHandler != null) {
            channel.pipeline().addLast("sni-handler", sniHandler);
        }
        configureCleartext(channel);
    }

    private void configureCleartext(Channel ch) {
        ChannelPipeline p = ch.pipeline();
        Http2MultiplexCodecBuilder serverMultiplexCodecBuilder = Http2MultiplexCodecBuilder.forServer(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) {
                    ServerTransport serverTransport = server.newTransport(httpAddress.getVersion());
                    channel.attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).set(serverTransport);
                    ChannelPipeline pipeline = channel.pipeline();
                    pipeline.addLast("multiplex-server-frame-converter",
                            new Http2StreamFrameToHttpObjectCodec(true));
                    if (serverConfig.isCompressionEnabled()) {
                        pipeline.addLast("multiplex-server-compressor", new HttpContentCompressor());
                    }
                    if (serverConfig.isDecompressionEnabled()) {
                        pipeline.addLast("multiplex-server-decompressor", new HttpContentDecompressor());
                    }
                    pipeline.addLast("multiplex-server-object-aggregator",
                            new HttpObjectAggregator(serverConfig.getMaxContentLength()));
                    pipeline.addLast("multiplex-server-chunked-write",
                            new ChunkedWriteHandler());
                    pipeline.addLast("multiplex-server-request-handler",
                            new ServerRequestHandler());
                }
            })
            .initialSettings(Http2Settings.defaultSettings());
        if (serverConfig.isDebug()) {
            serverMultiplexCodecBuilder.frameLogger(new Http2FrameLogger(LogLevel.DEBUG, "server"));
        }
        Http2MultiplexCodec serverMultiplexCodec = serverMultiplexCodecBuilder.build();
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

    public SslContext getSessionContext() {
        if (httpAddress.isSecure()) {
            return sniHandler.sslContext();
        }
        return null;
    }

    class ServerRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException {
            ServerTransport serverTransport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
            serverTransport.requestReceived(ctx, fullHttpRequest);
        }
    }

    class ServerMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ServerTransport serverTransport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
            if (msg instanceof DefaultHttp2SettingsFrame) {
                DefaultHttp2SettingsFrame http2SettingsFrame = (DefaultHttp2SettingsFrame) msg;
                serverTransport.settingsReceived(ctx, http2SettingsFrame.settings());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            ServerTransport serverTransport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws IOException {
            ServerTransport serverTransport = ctx.channel().attr(ServerTransport.TRANSPORT_ATTRIBUTE_KEY).get();
            serverTransport.exceptionReceived(ctx, cause);
        }
    }
}
