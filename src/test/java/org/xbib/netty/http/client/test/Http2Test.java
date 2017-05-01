package org.xbib.netty.http.client.test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameAdapter;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Test;
import org.xbib.netty.http.client.Http2Handler;
import org.xbib.netty.http.client.TrafficLoggingHandler;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class Http2Test {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %2$s %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(Level.ALL);
        }
    }


    private static final Logger logger = Logger.getLogger("");

    private final int serverExpectedDataFrames = 1;


    public void testGeneric() throws Exception {
        final InetSocketAddress inetSocketAddress = new InetSocketAddress("http2-push.io", 443);
        final CountDownLatch dataLatch = new CountDownLatch(serverExpectedDataFrames);
        EventLoopGroup group = new NioEventLoopGroup();
        Channel clientChannel = null;
        try {
            Bootstrap bs = new Bootstrap();
            bs.group(group);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new TrafficLoggingHandler());
                    SslContext sslContext = SslContextBuilder.forClient()
                            .sslProvider(SslProvider.OPENSSL)
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .keyManager((InputStream) null, null, null)
                            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            .applicationProtocolConfig(new ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1))
                            .build();
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                    SSLEngine engine = sslHandler.engine();
                    String fullQualifiedHostname = inetSocketAddress.getHostName();
                    SSLParameters params = engine.getSSLParameters();
                    params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                    engine.setSSLParameters(params);
                    ch.pipeline().addLast(sslHandler);
                    Http2ConnectionHandlerBuilder builder = new Http2ConnectionHandlerBuilder();
                    builder.server(false);
                    builder.frameListener(new Http2FrameAdapter() {
                        @Override
                        public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
                                throws Http2Exception {
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
                            dataLatch.countDown();
                            return super.onDataRead(ctx, streamId, data, padding, endOfStream);
                        }
                    });
                    builder.frameLogger(new Http2FrameLogger(LogLevel.INFO, "client"));
                    ch.pipeline().addLast(builder.build());
                }
            });
            clientChannel = bs.connect(inetSocketAddress).syncUninterruptibly().channel();
            dataLatch.await();
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            group.shutdownGracefully();
        }
    }


    public void testHttpAdapter() throws Exception {
        final InetSocketAddress inetSocketAddress = new InetSocketAddress("http2-push.io", 443);
        final CountDownLatch dataLatch = new CountDownLatch(serverExpectedDataFrames);
        EventLoopGroup group = new NioEventLoopGroup();
        Channel clientChannel = null;
        try {
            Bootstrap bs = new Bootstrap();
            bs.group(group);
            bs.channel(NioSocketChannel.class);
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new TrafficLoggingHandler());
                    SslContext sslContext = SslContextBuilder.forClient()
                            .sslProvider(SslProvider.OPENSSL)
                            .trustManager(InsecureTrustManagerFactory.INSTANCE)
                            .keyManager((InputStream) null, null, null)
                            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                            .applicationProtocolConfig(new ApplicationProtocolConfig(
                                    ApplicationProtocolConfig.Protocol.ALPN,
                                    ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                                    ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                                    ApplicationProtocolNames.HTTP_2,
                                    ApplicationProtocolNames.HTTP_1_1))
                            .build();
                    SslHandler sslHandler = sslContext.newHandler(ch.alloc());
                    SSLEngine engine = sslHandler.engine();
                    String fullQualifiedHostname = inetSocketAddress.getHostName();
                    SSLParameters params = engine.getSSLParameters();
                    params.setServerNames(Arrays.asList(new SNIServerName[]{new SNIHostName(fullQualifiedHostname)}));
                    engine.setSSLParameters(params);
                    ch.pipeline().addLast(sslHandler);
                    // settings handler
                    final Http2Connection http2Connection = new DefaultHttp2Connection(false);
                    Http2ConnectionHandler http2ConnectionHandler = new Http2ConnectionHandlerBuilder()
                            .frameLogger(new Http2FrameLogger(LogLevel.INFO, "client"))
                            .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                            new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                    .maxContentLength(1024 * 1024)
                                    .propagateSettings(true)
                                    .validateHttpHeaders(false)
                                    .build()))
                            .build();
                    ch.pipeline().addLast(http2ConnectionHandler);
                    ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                            dataLatch.countDown();
                        }
                    });
                }
            });
            clientChannel = bs.connect(inetSocketAddress).syncUninterruptibly().channel();
            dataLatch.await();
        } finally {
            if (clientChannel != null) {
                clientChannel.close();
            }
            group.shutdownGracefully();
        }
    }
}
