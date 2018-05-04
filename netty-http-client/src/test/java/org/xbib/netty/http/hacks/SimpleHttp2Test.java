package org.xbib.netty.http.hacks;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandlerBuilder;
import io.netty.handler.codec.http2.InboundHttp2ToHttpAdapterBuilder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.AttributeKey;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.TestBase;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Ignore
public class SimpleHttp2Test extends TestBase {

    private static final Logger logger = Logger.getLogger(SimpleHttp2Test.class.getName());

    @Test
    public void testHttp2WithUpgrade() throws Exception {
        Client client = new Client();
        try {
            Http2Transport transport = client.newTransport("webtide.com", 443);
            transport.onResponse(string -> logger.log(Level.INFO, "got messsage: " + string));
            transport.connect();
            transport.awaitSettings();
            sendRequest(transport);
            transport.awaitResponses();
            transport.close();
        } finally {
            client.shutdown();
        }
    }

    private void sendRequest(Http2Transport transport) {
        Channel channel = transport.channel();
        if (channel == null) {
            return;
        }
        Integer streamId = transport.nextStream();
        String host = transport.inetSocketAddress().getHostString();
        int port = transport.inetSocketAddress().getPort();
        String uri = "https://" + host + ":" + port;
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        request.headers().add(HttpHeaderNames.HOST, host + ":" + port);
        request.headers().add(HttpHeaderNames.USER_AGENT, "Java");
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
        if (streamId != null) {
            request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), Integer.toString(streamId));
        }
        logger.log(Level.INFO, () -> "writing request = " + request);
        channel.writeAndFlush(request);
    }

    private AttributeKey<Http2Transport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    interface ResponseWriter {
        void write(String string);
    }

    class Client {
        private final EventLoopGroup eventLoopGroup;

        private final Bootstrap bootstrap;

        private final Http2SettingsHandler http2SettingsHandler;

        private final Http2ResponseHandler http2ResponseHandler;

        private final Initializer initializer;

        Client() {
            eventLoopGroup = new NioEventLoopGroup();
            http2SettingsHandler = new Http2SettingsHandler();
            http2ResponseHandler = new Http2ResponseHandler();
            initializer = new Initializer(http2SettingsHandler, http2ResponseHandler);
            bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(initializer);
        }

        Bootstrap bootstrap() {
            return bootstrap;
        }

        void shutdown() {
            eventLoopGroup.shutdownGracefully();
        }

        Http2Transport newTransport(String host, int port) {
            Http2Transport transport = new Http2Transport(this, new InetSocketAddress(host, port));
            return transport;
        }
    }

    class Http2Transport {

        private final Client client;

        private final InetSocketAddress inetSocketAddress;

        private Channel channel;

        CompletableFuture<Boolean> settingsPromise;

        private SortedMap<Integer, CompletableFuture<Boolean>> streamidPromiseMap;

        private AtomicInteger streamIdCounter;

        private ResponseWriter responseWriter;

        Http2Transport(Client client, InetSocketAddress inetSocketAddress) {
            this.client = client;
            this.inetSocketAddress = inetSocketAddress;
            streamidPromiseMap = new TreeMap<>();
            streamIdCounter = new AtomicInteger(3);
        }

        InetSocketAddress inetSocketAddress() {
            return inetSocketAddress;
        }

        void connect() throws InterruptedException {
            channel = client.bootstrap().connect(inetSocketAddress).sync().await().channel();
            channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
            settingsPromise = new CompletableFuture<>();
        }

        Channel channel() {
            return channel;
        }

        Integer nextStream() {
            Integer streamId = streamIdCounter.getAndAdd(2);
            streamidPromiseMap.put(streamId, new CompletableFuture<>());
            return streamId;
        }

        void onResponse(ResponseWriter responseWriter) {
            this.responseWriter = responseWriter;
        }

        void settingsReceived(Channel channel, Http2Settings http2Settings) {
            if (settingsPromise != null) {
                settingsPromise.complete(true);
            } else {
                logger.log(Level.WARNING, "settings received but no promise present");
            }
        }

        void awaitSettings() {
            if (settingsPromise != null) {
                try {
                    logger.log(Level.INFO, "waiting for settings");
                    settingsPromise.get(5, TimeUnit.SECONDS);
                    logger.log(Level.INFO, "settings received");
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    settingsPromise.completeExceptionally(e);
                }
            } else {
                logger.log(Level.WARNING, "waiting for settings but no promise present");
            }
        }

        void responseReceived(Integer streamId, String message) {
            if (streamId == null) {
                logger.log(Level.WARNING, "unexpected message received: " + message);
                return;
            }
            CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
            if (promise == null) {
                logger.log(Level.WARNING, "message received for unknown stream id " + streamId);
            } else {
                if (responseWriter != null) {
                    responseWriter.write(message);
                }
                promise.complete(true);
            }
        }

        void awaitResponse(Integer streamId) {
            if (streamId == null) {
                return;
            }
            CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
            if (promise != null) {
                try {
                    logger.log(Level.INFO, "waiting for response for stream id=" + streamId);
                    promise.get(5, TimeUnit.SECONDS);
                    logger.log(Level.INFO, "response for stream id=" + streamId + " received");
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.log(Level.WARNING, "streamId=" + streamId + " " + e.getMessage(), e);
                } finally {
                    streamidPromiseMap.remove(streamId);
                }
            }
        }

        void awaitResponses() {
            logger.log(Level.INFO, "waiting for all stream ids " + streamidPromiseMap.keySet());
            for (int streamId : streamidPromiseMap.keySet()) {
                awaitResponse(streamId);
            }
        }

        void fail(Throwable throwable) {
            for (CompletableFuture<Boolean> promise : streamidPromiseMap.values()) {
                promise.completeExceptionally(throwable);
            }
        }

        void close() {
            if (channel != null) {
                channel.close();
            }
        }
    }

    class Initializer extends ChannelInitializer<SocketChannel> {

        private Http2SettingsHandler http2SettingsHandler;

        private Http2ResponseHandler http2ResponseHandler;

        Initializer(Http2SettingsHandler http2SettingsHandler, Http2ResponseHandler http2ResponseHandler) {
            this.http2SettingsHandler = http2SettingsHandler;
            this.http2ResponseHandler = http2ResponseHandler;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            DefaultHttp2Connection http2Connection = new DefaultHttp2Connection(false);
            Http2FrameLogger frameLogger = new Http2FrameLogger(LogLevel.INFO, "client");
            Http2ConnectionHandler http2ConnectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
                    .connection(http2Connection)
                    .frameLogger(frameLogger)
                    .frameListener(new DelegatingDecompressorFrameListener(http2Connection,
                            new InboundHttp2ToHttpAdapterBuilder(http2Connection)
                                    .maxContentLength(10 * 1024 * 1024)
                                    .propagateSettings(true)
                                    .build()))
                    .build();

            try {
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
                ch.pipeline().addLast(sslContext.newHandler(ch.alloc()));
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

    class Http2SettingsHandler extends SimpleChannelInboundHandler<Http2Settings> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2Settings http2Settings) {
            Http2Transport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.settingsReceived(ctx.channel(), http2Settings);
            ctx.pipeline().remove(this);
        }
    }

    class Http2ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            Http2Transport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
            if (msg.content().isReadable()) {
                transport.responseReceived(streamId, msg.content().toString(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx)  {
            // do nothing
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)  {
            ctx.fireChannelInactive();
            Http2Transport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.fail(new IOException("channel closed"));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.log(Level.SEVERE, cause.getMessage(), cause);
            Http2Transport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.fail(cause);
            ctx.channel().close();
        }
    }
}
