package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.AsciiString;
import io.netty.util.DomainNameMapping;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;
import org.xbib.netty.http.server.handler.ExtendedSNIHandler;
import org.xbib.netty.http.server.handler.TrafficLoggingHandler;
import org.xbib.netty.http.server.transport.Transport;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<Channel> {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final Server server;

    private final ServerConfig serverConfig;

    private final HttpAddress httpAddress;

    private final DomainNameMapping<SslContext> domainNameMapping;

    public Http2ChannelInitializer(Server server,
                                   HttpAddress httpAddress,
                                   DomainNameMapping<SslContext> domainNameMapping) {
        this.server = server;
        this.serverConfig = server.getServerConfig();
        this.httpAddress = httpAddress;
        this.domainNameMapping = domainNameMapping;
    }

    @Override
    public void initChannel(Channel channel) {
        Transport transport = server.newTransport(httpAddress.getVersion());
        channel.attr(Transport.TRANSPORT_ATTRIBUTE_KEY).set(transport);
        if (serverConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (server.getServerConfig().isDebug() && logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "HTTP/2 server channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel) {
        channel.pipeline().addLast("sni-handler",
                new ExtendedSNIHandler(domainNameMapping, serverConfig, httpAddress));
        configureCleartext(channel);
    }

    private void configureCleartext(Channel ch) {
        ChannelHandler channelHandler = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) {
                Transport transport = server.newTransport(httpAddress.getVersion());
                channel.attr(Transport.TRANSPORT_ATTRIBUTE_KEY).set(transport);
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast("server-frame-converter",
                        new Http2StreamFrameToHttpObjectCodec(true));
                if (serverConfig.isCompressionEnabled()) {
                    pipeline.addLast("server-compressor", new HttpContentCompressor());
                }
                if (serverConfig.isDecompressionEnabled()) {
                    pipeline.addLast("server-decompressor", new HttpContentDecompressor());
                }
                pipeline.addLast("server-object-aggregator",
                        new HttpObjectAggregator(serverConfig.getMaxContentLength()));
                pipeline.addLast("server-chunked-write", new ChunkedWriteHandler());
                pipeline.addLast("server-request-handler", new ServerRequestHandler());
            }
        };
        Http2MultiplexCodecBuilder multiplexCodecBuilder = Http2MultiplexCodecBuilder.forServer(channelHandler)
            .initialSettings(Http2Settings.defaultSettings());
        if (serverConfig.isDebug()) {
            multiplexCodecBuilder.frameLogger(new Http2FrameLogger(LogLevel.DEBUG, "server"));
        }
        Http2MultiplexCodec multiplexCodec = multiplexCodecBuilder.build();

        HttpServerCodec serverCodec = new HttpServerCodec();
        HttpServerUpgradeHandler upgradeHandler = new HttpServerUpgradeHandler(serverCodec, protocol -> {
            if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
                return new Http2ServerUpgradeCodec(multiplexCodec);
            } else {
                return null;
            }
        });
        CleartextHttp2ServerUpgradeHandler cleartextHttp2ServerUpgradeHandler =
                new CleartextHttp2ServerUpgradeHandler(serverCodec, upgradeHandler, multiplexCodec);
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("server-upgrade", cleartextHttp2ServerUpgradeHandler);
        pipeline.addLast("server-messages", new ServerMessages());
    }

    static class ServerRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullHttpRequest) throws IOException {
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            transport.requestReceived(ctx, fullHttpRequest, null);
        }
    }

    static class ServerMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof DefaultHttp2SettingsFrame) {
                DefaultHttp2SettingsFrame http2SettingsFrame = (DefaultHttp2SettingsFrame) msg;
                Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
                transport.settingsReceived(ctx, http2SettingsFrame.settings());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws IOException {
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            transport.exceptionReceived(ctx, cause);
        }
    }
}
