package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.logging.LogLevel;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.api.HttpChannelInitializer;
import org.xbib.netty.http.client.handler.http.TrafficLoggingHandler;
import org.xbib.netty.http.client.api.Transport;
import org.xbib.netty.http.common.HttpAddress;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<Channel> implements HttpChannelInitializer {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final Client.SslHandlerFactory sslHandlerFactory;

    public Http2ChannelInitializer(ClientConfig clientConfig,
                                   HttpAddress httpAddress,
                                   Client.SslHandlerFactory sslHandlerFactory,
                                   HttpChannelInitializer unusedInitializer) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.sslHandlerFactory = sslHandlerFactory;
    }

    @Override
    public void initChannel(Channel channel) {
        if (clientConfig.isDebug()) {
            channel.pipeline().addLast(new TrafficLoggingHandler(LogLevel.DEBUG));
        }
        if (httpAddress.isSecure()) {
            configureEncrypted(channel);
        } else {
            configureCleartext(channel);
        }
        if (clientConfig.isDebug()) {
            logger.log(Level.FINE, "HTTP/2 client channel initialized: " +
                    " address=" + httpAddress + " pipeline=" + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel) {
        channel.pipeline().addLast(sslHandlerFactory.create());
        configureCleartext(channel);
    }

    public void configureCleartext(Channel ch) {
        ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                throw new IllegalStateException();
            }
        };
        Http2MultiplexCodecBuilder multiplexCodecBuilder = Http2MultiplexCodecBuilder.forClient(initializer)
                .initialSettings(clientConfig.getHttp2Settings());
        if (clientConfig.isDebug()) {
            multiplexCodecBuilder.frameLogger(new PushPromiseHandler(LogLevel.DEBUG, "client"));
        }
        Http2MultiplexCodec multiplexCodec = multiplexCodecBuilder.autoAckSettingsFrame(true) .build();
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("client-multiplex", multiplexCodec);
        pipeline.addLast("client-messages", new ClientMessages());
    }

    static class ClientMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof DefaultHttp2SettingsFrame) {
                DefaultHttp2SettingsFrame settingsFrame = (DefaultHttp2SettingsFrame) msg;
                Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
                if (transport != null) {
                    transport.settingsReceived(settingsFrame.settings());
                }
            } else {
                logger.log(Level.FINE, "received msg " + msg.getClass().getName());
            }
        }

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent) {
                Http2ConnectionPrefaceAndSettingsFrameWrittenEvent event =
                        (Http2ConnectionPrefaceAndSettingsFrameWrittenEvent)evt;
                Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
                if (transport != null) {
                    transport.settingsReceived(null);
                }
            }
            ctx.fireUserEventTriggered(evt);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            if (transport != null) {
                transport.fail(cause);
            }
        }
    }

    static class PushPromiseHandler extends Http2FrameLogger {

        PushPromiseHandler(LogLevel level, String name) {
            super(level, name);
        }

        public void logPushPromise(Direction direction, ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                   Http2Headers headers, int padding) {
            super.logPushPromise(direction, ctx, streamId, promisedStreamId, headers, padding);
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            if (transport != null) {
                transport.pushPromiseReceived(ctx.channel(), streamId, promisedStreamId, headers);
            }
        }
    }
}
