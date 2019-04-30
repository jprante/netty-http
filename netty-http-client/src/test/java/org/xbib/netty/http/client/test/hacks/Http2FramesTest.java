package org.xbib.netty.http.client.test.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

class Http2FramesTest {

    private static final Logger logger = Logger.getLogger(Http2FramesTest.class.getName());

    @Test
    void testHttp2Frames() throws Exception {
        final InetSocketAddress inetSocketAddress = new InetSocketAddress("webtide.com", 443);
        CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        Channel clientChannel = null;
        try {
            Bootstrap bootstrap = new Bootstrap()
                .group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                    SslContext sslContext = SslContextBuilder.forClient()
                        .sslProvider(SslProvider.JDK)
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                        .applicationProtocolConfig(new ApplicationProtocolConfig(
                            ApplicationProtocolConfig.Protocol.ALPN,
                            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2))
                        .build();
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                    SSLEngine engine = sslHandler.engine();
                    String fullQualifiedHostname = inetSocketAddress.getHostName();
                    SSLParameters params = engine.getSSLParameters();
                    params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                    engine.setSSLParameters(params);
                    ch.pipeline().addLast(sslHandler);
                    Http2FrameAdapter frameAdapter = new Http2FrameAdapter() {
                        @Override
                        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
                            logger.log(Level.FINE, "settings received, now writing request");
                            Http2ConnectionHandler handler = ctx.pipeline().get(Http2ConnectionHandler.class);
                            handler.encoder().writeHeaders(ctx, 3,
                                    new DefaultHttp2Headers().method(HttpMethod.GET.asciiName())
                                            .path("/")
                                            .scheme("https")
                                            .authority(inetSocketAddress.getHostName()),
                                    0, true, ctx.newPromise());
                            ctx.channel().flush();
                        }

                        @Override
                        public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding,
                                              boolean endOfStream) throws Http2Exception {
                            int i = super.onDataRead(ctx, streamId, data, padding, endOfStream);
                            if (endOfStream) {
                                completableFuture.complete(true);
                            }
                            return i;
                        }
                    };
                    Http2ConnectionHandlerBuilder builder = new Http2ConnectionHandlerBuilder()
                            .server(false)
                            .frameListener(frameAdapter)
                            .frameLogger(new Http2FrameLogger(LogLevel.INFO, "client"));
                    ch.pipeline().addLast(builder.build());
                    }
                });
            logger.log(Level.INFO, () -> "connecting");
            clientChannel = bootstrap.connect(inetSocketAddress).sync().channel();
            logger.log(Level.INFO, () -> "waiting for end of stream");
            completableFuture.get();
            logger.log(Level.INFO, () -> "done");
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            eventLoopGroup.shutdownGracefully();
        }
    }
}
