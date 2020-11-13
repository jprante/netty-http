package org.xbib.netty.http.client.test.hacks;

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
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

@ExtendWith(NettyHttpTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleHttp1Test {

    private static final Logger logger = Logger.getLogger(SimpleHttp1Test.class.getName());

    @AfterAll
    void checkThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        logger.log(Level.INFO, "threads = " + threadSet.size() );
        threadSet.forEach( thread -> {
            if (thread.getName().equals("ObjectCleanerThread")) {
                logger.log(Level.INFO, thread.toString());
            }
        });
    }

    @Test
    void testHttp1() throws Exception {
        Client client = new Client();
        try {
            HttpTransport transport = client.newTransport("google.de", 80);
            transport.onResponse(msg -> logger.log(Level.INFO,
                    "got response: " + msg.status().code() + " headers=" + msg.headers().entries()));
            transport.connect();
            sendRequest(transport);
            transport.awaitResponse();
        } finally {
            client.shutdown();
        }
    }

    private void sendRequest(HttpTransport transport) {
        Channel channel = transport.channel();
        if (channel == null) {
            return;
        }
        String host = transport.inetSocketAddress().getHostString();
        int port = transport.inetSocketAddress().getPort();
        String uri = "http://" + host + ":" + port;
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        request.headers().add(HttpHeaderNames.HOST, host + ":" + port);
        request.headers().add(HttpHeaderNames.USER_AGENT, "Java");
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
        request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
        logger.log(Level.INFO, () -> "writing request = " + request);
        if (channel.isWritable()) {
            channel.writeAndFlush(request);
        }
    }

    private final AttributeKey<HttpTransport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    interface ResponseWriter {
        void write(FullHttpResponse msg);
    }

    class Client {
        private final EventLoopGroup eventLoopGroup;

        private final Bootstrap bootstrap;

        private final List<HttpTransport> transports;

        Client() {
            eventLoopGroup = new NioEventLoopGroup();
            HttpResponseHandler httpResponseHandler = new HttpResponseHandler();
            Initializer initializer = new Initializer(httpResponseHandler);
            bootstrap = new Bootstrap()
                    .group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .handler(initializer);
            transports = new ArrayList<>();
        }

        Bootstrap bootstrap() {
            return bootstrap;
        }

        void shutdown() {
            close();
            eventLoopGroup.shutdownGracefully();
            try {
                eventLoopGroup.awaitTermination(10L, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }

        HttpTransport newTransport(String host, int port) {
            HttpTransport transport = new HttpTransport(this, new InetSocketAddress(host, port));
            transports.add(transport);
            return transport;
        }

        void close() {
            for (HttpTransport transport : transports) {
                transport.close();
            }
            transports.clear();
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

        InetSocketAddress inetSocketAddress() {
            return inetSocketAddress;
        }

        void connect() throws InterruptedException {
            channel = client.bootstrap().connect(inetSocketAddress).sync().await().channel();
            channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
            promise = new CompletableFuture<>();
        }

        Channel channel() {
            return channel;
        }

        void onResponse(ResponseWriter responseWriter) {
            this.responseWriter = responseWriter;
        }

        void responseReceived(FullHttpResponse msg) {
            if (responseWriter != null) {
                responseWriter.write(msg);
            }
        }

        void awaitResponse() {
            if (promise != null) {
                try {
                    promise.get(5, TimeUnit.SECONDS);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            }
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
        }
    }

    class Initializer extends ChannelInitializer<SocketChannel> {

        private HttpResponseHandler httpResponseHandler;

        Initializer(HttpResponseHandler httpResponseHandler) {
            this.httpResponseHandler = httpResponseHandler;
        }

        @Override
        protected void initChannel(SocketChannel ch) {
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
                transport.responseReceived(msg);
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
}
