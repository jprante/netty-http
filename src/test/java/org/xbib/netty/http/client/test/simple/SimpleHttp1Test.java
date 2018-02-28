package org.xbib.netty.http.client.test.simple;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class SimpleHttp1Test {

    private static final Logger logger = Logger.getLogger(SimpleHttp1Test.class.getName());

    @Test
    public void testHttp1() throws Exception {
        Client client = new Client();
        try {
            HttpTransport transport = client.newTransport("fl.hbz-nrw.de", 80);
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

    private void sendRequest(HttpTransport transport) {
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

    private AttributeKey<HttpTransport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    interface ResponseWriter {
        void write(String string);
    }

    class Client {
        private final EventLoopGroup eventLoopGroup;

        private final Bootstrap bootstrap;

        private final HttpResponseHandler httpResponseHandler;

        private final Initializer initializer;

        private final List<HttpTransport> transports;

        Client() {
            eventLoopGroup = new NioEventLoopGroup();
            httpResponseHandler = new HttpResponseHandler();
            initializer = new Initializer(httpResponseHandler);
            bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(initializer);
            transports = new ArrayList<>();
        }

        Bootstrap bootstrap() {
            return bootstrap;
        }

        Initializer initializer() {
            return initializer;
        }

        HttpResponseHandler responseHandler() {
            return httpResponseHandler;
        }

        void shutdown() {
            eventLoopGroup.shutdownGracefully();
        }

        HttpTransport newTransport(String host, int port) {
            HttpTransport transport = new HttpTransport(this, new InetSocketAddress(host, port));
            transports.add(transport);
            return transport;
        }

        List<HttpTransport> transports() {
            return transports;
        }

        void close(HttpTransport transport) {
            transports.remove(transport);
        }

        void close() {
            for (HttpTransport transport : transports) {
                transport.close();
            }
        }
    }

    class HttpTransport {

        private final Client client;

        private final InetSocketAddress inetSocketAddress;

        private Channel channel;

        private CompletableFuture<Boolean> promise;

        private ResponseWriter responseWriter;

        HttpTransport(Client client, InetSocketAddress inetSocketAddress ) {
            this.client = client;
            this.inetSocketAddress = inetSocketAddress;
        }

        Client client() {
            return client;
        }

        InetSocketAddress inetSocketAddress() {
            return inetSocketAddress;
        }

        void connect() throws InterruptedException {
            channel = client.bootstrap().connect(inetSocketAddress).sync().await().channel();
            channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
        }

        Channel channel() {
            return channel;
        }

        Integer nextStream() {
            promise = new CompletableFuture<>();
            return null;
        }

        void onResponse(ResponseWriter responseWriter) {
            this.responseWriter = responseWriter;
        }

        void settingsReceived(Channel channel, Http2Settings http2Settings) {
        }

        void awaitSettings() {
        }

        void responseReceived(Integer streamId, String message) {
            if (promise == null) {
                logger.log(Level.WARNING, "message received for unknown stream id " + streamId);
            } else {
                if (responseWriter != null) {
                    responseWriter.write(message);
                }
            }
        }
        void awaitResponse(Integer streamId) {
            if (promise != null) {
                try {
                    logger.log(Level.INFO, "waiting for response");
                    promise.get(5, TimeUnit.SECONDS);
                    logger.log(Level.INFO, "response received");
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }

        void awaitResponses() {
            awaitResponse(null);
        }

        void complete() {
            if (promise != null) {
                promise.complete(true);
            }
        }

        void fail(Throwable throwable) {
            if (promise != null) {
                promise.completeExceptionally(throwable);
            }
        }

        void close() {
            if (channel != null) {
                channel.close();
            }
            client.close(this);
        }
    }

    class Initializer extends ChannelInitializer<SocketChannel> {

        private HttpResponseHandler httpResponseHandler;

        Initializer(HttpResponseHandler httpResponseHandler) {
            this.httpResponseHandler = httpResponseHandler;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
            ch.pipeline().addLast(new TrafficLoggingHandler());
            ch.pipeline().addLast(new HttpClientCodec());
            ch.pipeline().addLast(new HttpObjectAggregator(1048576));
            ch.pipeline().addLast(httpResponseHandler);
        }
    }

    class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            HttpTransport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            if (msg.content().isReadable()) {
                transport.responseReceived(null, msg.content().toString(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx)  {
            HttpTransport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.complete();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx)  {
            ctx.fireChannelInactive();
            HttpTransport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.fail(new IOException("channel closed"));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            logger.log(Level.SEVERE, cause.getMessage(), cause);
            HttpTransport transport = ctx.channel().attr(TRANSPORT_ATTRIBUTE_KEY).get();
            transport.fail(cause);
            ctx.channel().close();
        }
    }

    class TrafficLoggingHandler extends LoggingHandler {

        TrafficLoggingHandler() {
            super("client", LogLevel.INFO);
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) {
            ctx.fireChannelRegistered();
        }

        @Override
        public void channelUnregistered(ChannelHandlerContext ctx) {
            ctx.fireChannelUnregistered();
        }

        @Override
        public void flush(ChannelHandlerContext ctx) {
            ctx.flush();
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf && !((ByteBuf) msg).isReadable()) {
                ctx.write(msg, promise);
            } else {
                super.write(ctx, msg, promise);
            }
        }
    }
}
