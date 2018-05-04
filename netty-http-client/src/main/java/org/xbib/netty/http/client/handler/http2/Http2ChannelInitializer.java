package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http2.DefaultHttp2SettingsFrame;
import io.netty.handler.codec.http2.Http2ConnectionAdapter;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameCodec;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2MultiplexCodecBuilder;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.SslHandler;
import org.xbib.netty.http.client.ClientConfig;
import org.xbib.netty.http.client.handler.http.TrafficLoggingHandler;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ChannelInitializer extends ChannelInitializer<Channel> {

    private static final Logger logger = Logger.getLogger(Http2ChannelInitializer.class.getName());

    private final ClientConfig clientConfig;

    private final HttpAddress httpAddress;

    private final SslHandler sslHandler;

    public Http2ChannelInitializer(ClientConfig clientConfig,
                            HttpAddress httpAddress,
                            SslHandler sslHandler) {
        this.clientConfig = clientConfig;
        this.httpAddress = httpAddress;
        this.sslHandler = sslHandler;
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
            logger.log(Level.FINE, "HTTP/2 client channel initialized: " + channel.pipeline().names());
        }
    }

    private void configureEncrypted(Channel channel) {
        channel.pipeline().addLast(sslHandler);
        configureCleartext(channel);
    }

    public void configureCleartext(Channel ch) {
        ChannelInitializer<Channel> initializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) {
                throw new IllegalStateException();
            }
        };
        Http2MultiplexCodecBuilder clientMultiplexCodecBuilder = Http2MultiplexCodecBuilder.forClient(initializer)
                .initialSettings(clientConfig.getHttp2Settings());
        if (clientConfig.isDebug()) {
            clientMultiplexCodecBuilder.frameLogger(new Http2FrameLogger(LogLevel.DEBUG, "client"));
        }
        Http2MultiplexCodec http2MultiplexCodec = clientMultiplexCodecBuilder.build();
        ChannelPipeline p = ch.pipeline();
        p.addLast("client-codec", http2MultiplexCodec);
        //p.addLast("client-push-promise", new PushPromiseHandler());
        p.addLast("client-messages", new ClientMessages());
    }

    class ClientMessages extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof DefaultHttp2SettingsFrame) {
                DefaultHttp2SettingsFrame settingsFrame = (DefaultHttp2SettingsFrame) msg;
                Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
                if (transport != null) {
                    transport.settingsReceived(settingsFrame.settings());
                }
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

    class PushPromiseHandler extends Http2FrameAdapter {

        @Override
        public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                      Http2Headers headers, int padding) throws Http2Exception {
            super.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
            Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
            transport.pushPromiseReceived(ctx.channel(), streamId, promisedStreamId, headers);
        }
    }
}
