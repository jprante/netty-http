package org.xbib.netty.http.server.handler.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerUpgradeHandler;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionDecoder;
import io.netty.handler.codec.http2.DefaultHttp2ConnectionEncoder;
import io.netty.handler.codec.http2.DefaultHttp2FrameReader;
import io.netty.handler.codec.http2.DefaultHttp2FrameWriter;
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2FrameReader;
import io.netty.handler.codec.http2.Http2FrameWriter;
import io.netty.handler.codec.http2.Http2InboundFrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2OutboundFrameLogger;
import io.netty.handler.codec.http2.Http2ServerUpgradeCodec;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapter;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.AsciiString;
import io.netty.util.DomainNameMapping;
import io.netty.util.ReferenceCountUtil;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;
import org.xbib.netty.http.server.handler.http1.HttpHandler;
import org.xbib.netty.http.server.handler.http1.TrafficLoggingHandler;

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

    /**
     * The channel initialization for HTTP/2.
     *
     * @param channel socket channel
     */
    @Override
    public void initChannel(Channel channel) {
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
        channel.pipeline().addLast(new SniHandler(domainNameMapping));
        configureCleartext(channel);
    }

    private void configureCleartext(Channel ch) {
        Http2SettingsHandler http2SettingsHandler = new Http2SettingsHandler();
        Http2RequestHandler http2RequestHandler = new Http2RequestHandler();
        //HttpHandler httpHandler = new HttpHandler(server);

        ch.pipeline()
                //.addLast(newConnectionHandler())
                 .addLast(upgradeHandler());
                //.addLast(http2SettingsHandler)
                //.addLast(http2RequestHandler);
               // .addLast(sourceCodec)

        /*final Http2MultiplexCodec http2Codec = Http2MultiplexCodecBuilder.forServer(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                logger.log(Level.INFO, "initChannel multiplex ");
            }
        }).build();
        HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol ->
                new Http2ServerUpgradeCodec(http2Codec);
        final HttpServerCodec serverCodec = new HttpServerCodec();
        ch.pipeline().addLast(serverCodec)
            .addLast(new HttpServerUpgradeHandler(serverCodec, upgradeCodecFactory))
            .addLast(new SimpleChannelInboundHandler<HttpMessage>() {
                @Override
                protected void channelRead0(ChannelHandlerContext ctx, HttpMessage msg) throws Exception {
                    // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
                    System.err.println("Directly talking: " + msg.protocolVersion() + " (no upgrade was attempted)");
                    ChannelPipeline pipeline = ctx.pipeline();
                    ChannelHandlerContext thisCtx = pipeline.context(this);
                    pipeline.addAfter(thisCtx.name(), null, new HelloWorldHttp1Handler("Direct. No Upgrade Attempted."));
                    pipeline.replace(this, null, new HttpObjectAggregator(Integer.MAX_VALUE));
                    ctx.fireChannelRead(ReferenceCountUtil.retain(msg));
                }
            })
            .addLast(new UserEventLogger())
            .addLast(new HttpHandler(server));
*/

        /*
        Http2FrameCodec http2FrameCodec = Http2FrameCodecBuilder.forServer().build();

        Http2StreamFrameToHttpObjectCodec http2StreamFrameToHttpObjectCodec =
                new Http2StreamFrameToHttpObjectCodec(true, true);

        HttpObjectAggregator httpObjectAggregator =
                new HttpObjectAggregator(serverConfig.getMaxContentLength(), false);
        httpObjectAggregator.setMaxCumulationBufferComponents(serverConfig.getMaxCompositeBufferComponents());
        HttpHandler httpHandler = new HttpHandler(server);

        Http2ConnectionHandler http2ConnectionHandler = newConnectionHandler(server.getServerConfig());
        Http2Connection http2Connection = http2ConnectionHandler.connection();
        Http2Handler http2Handler = new Http2Handler(serverConfig, http2Connection, true);
        http2Connection.addListener(http2Handler);
        http2ConnectionHandler.decoder().frameListener(new DelegatingDecompressorFrameListener(http2Connection, http2Handler));

        channel.pipeline().addLast(http2ConnectionHandler)
        .addLast(new UserEventLogger())
        .addLast(new HttpHandler(server));

        //.addLast(new Http2StreamFrameToHttpObjectCodec(true))
                //.addLast(httpObjectAggregator)
                //.addLast(httpHandler);
        */
    }

    private Http2ConnectionHandler newStandardConnectionHandler() {
        Http2Connection http2Connection = new DefaultHttp2Connection(true);
        InboundHttp2ToHttpAdapter inboundHttp2ToHttpAdapter =
                new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                        .maxContentLength(serverConfig.getMaxContentLength())
                        .propagateSettings(true)
                        .validateHttpHeaders(true)
                        .build();
        Http2ConnectionHandlerBuilder builder = new Http2ConnectionHandlerBuilder()
                .connection(http2Connection)
                .initialSettings(serverConfig.getHttp2Settings())
                .frameListener(new DelegatingDecompressorFrameListener(http2Connection, inboundHttp2ToHttpAdapter));
        if (serverConfig.isDebug()) {
            builder.frameLogger(new Http2FrameLogger(serverConfig.getDebugLogLevel(), "server"));
        }
        return builder.build();
    }

    private Http2ConnectionHandler newConnectionHandler() {
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

        Http2ConnectionHandlerBuilder builder = new Http2ConnectionHandlerBuilder()
                .connection(http2Connection)
                //.codec(decoder, encoder)
                //.initialSettings(initialSettings)
                .frameListener(new FrameListener())
                .frameLogger(new Http2FrameLogger(serverConfig.getDebugLogLevel(), "server"));
        if (serverConfig.getIdleTimeoutMillis() > 0) {
            builder.gracefulShutdownTimeoutMillis(serverConfig.getIdleTimeoutMillis());
        }
        return builder.build();
        //Http2Handler http2Handler = new Http2Handler(server, http2Connection, true);
        //http2ConnectionHandler.connection().addListener(http2Handler);
        //http2ConnectionHandler.decoder().frameListener();
        //return http2ConnectionHandler;
    }

    static class Http2ServerConnectionHandler extends Http2ConnectionHandler {

        Http2ServerConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder,
                                     Http2Settings initialSettings) {
            super(decoder, encoder, initialSettings);
        }
    }

    private final HttpServerUpgradeHandler.UpgradeCodecFactory upgradeCodecFactory = protocol -> {
        if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol)) {
            return upgradeCodec();
        } else {
            return null;
        }
    };

    private Http2ServerUpgradeCodec upgradeCodec() {
        return new Http2ServerUpgradeCodec(Http2MultiplexCodecBuilder.forServer(http2MultiplexCodec()).build());
    }

    private HttpServerUpgradeHandler upgradeHandler() {
        HttpServerCodec sourceCodec = new HttpServerCodec();
        return new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory);
    }

    private Http2MultiplexCodec http2MultiplexCodec() {
        Http2FrameLogger frameLogger = new Http2FrameLogger(serverConfig.getDebugLogLevel(), "server");
        return Http2MultiplexCodecBuilder.forServer(new DummyHandler())
                .frameLogger(frameLogger)
                .initialSettings(serverConfig.getHttp2Settings())
                .build();
    }

}
