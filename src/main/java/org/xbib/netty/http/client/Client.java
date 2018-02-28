package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.ssl.OpenSsl;
import org.xbib.netty.http.client.handler.http1.HttpChannelInitializer;
import org.xbib.netty.http.client.handler.http1.HttpResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.client.handler.http2.Http2ResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2SettingsHandler;
import org.xbib.netty.http.client.transport.Http2Transport;
import org.xbib.netty.http.client.transport.HttpTransport;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.client.util.NetworkUtils;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Client {

    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private static final ThreadFactory httpClientThreadFactory = new HttpClientThreadFactory();

    static {
        NetworkUtils.extendSystemProperties();
    }

    private final ClientConfig clientConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup eventLoopGroup;

    private final Class<? extends SocketChannel> socketChannelClass;

    private final Bootstrap bootstrap;

    private final HttpResponseHandler httpResponseHandler;

    private final Http2SettingsHandler http2SettingsHandler;

    private final Http2ResponseHandler http2ResponseHandler;

    private final List<Transport> transports;

    private TransportListener transportListener;

    public Client() {
        this(new ClientConfig());
    }

    public Client(ClientConfig clientConfig) {
        this(clientConfig, null, null, null);
    }

    public Client(ClientConfig clientConfig, ByteBufAllocator byteBufAllocator,
                  EventLoopGroup eventLoopGroup, Class<? extends SocketChannel> socketChannelClass) {
        Objects.requireNonNull(clientConfig);
        this.clientConfig = clientConfig;
        this.byteBufAllocator = byteBufAllocator != null ?
                byteBufAllocator : PooledByteBufAllocator.DEFAULT;
        this.eventLoopGroup = eventLoopGroup != null ?
                eventLoopGroup : new NioEventLoopGroup(clientConfig.getThreadCount(), httpClientThreadFactory);
        this.socketChannelClass = socketChannelClass != null ?
                socketChannelClass : NioSocketChannel.class;
        this.bootstrap = new Bootstrap()
                .group(this.eventLoopGroup)
                .channel(this.socketChannelClass)
                .option(ChannelOption.TCP_NODELAY, clientConfig.isTcpNodelay())
                .option(ChannelOption.SO_KEEPALIVE, clientConfig.isKeepAlive())
                .option(ChannelOption.SO_REUSEADDR, clientConfig.isReuseAddr())
                .option(ChannelOption.SO_SNDBUF, clientConfig.getTcpSendBufferSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.ALLOCATOR, byteBufAllocator);
        this.httpResponseHandler = new HttpResponseHandler();
        this.http2SettingsHandler = new Http2SettingsHandler();
        this.http2ResponseHandler = new Http2ResponseHandler();
        this.transports = new CopyOnWriteArrayList<>();
    }

    public static ClientBuilder builder() {
        return new ClientBuilder();
    }

    public void setTransportListener(TransportListener transportListener) {
        this.transportListener = transportListener;
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable() +
                " OpenSSL ALPN support: " + OpenSsl.isAlpnSupported() +
                " Local host name: " + NetworkUtils.getLocalHostName("localhost"));
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public int getTimeout() {
        return clientConfig.getReadTimeoutMillis();
    }

    public Transport newTransport(HttpAddress httpAddress) {
        Transport transport;
        if (httpAddress.getVersion().majorVersion() < 2) {
            transport = new HttpTransport(this, httpAddress);
        } else {
            transport = new Http2Transport(this, httpAddress);
        }
        if (transportListener != null) {
            transportListener.onOpen(transport);
        }
        transports.add(transport);
        return transport;
    }

    public Channel newChannel(HttpAddress httpAddress) throws InterruptedException {
        HttpVersion httpVersion = httpAddress.getVersion();
        ChannelInitializer<SocketChannel> initializer;
        if (httpVersion.majorVersion() < 2) {
            initializer = new HttpChannelInitializer(clientConfig, httpAddress, httpResponseHandler);
        } else {
            initializer = new Http2ChannelInitializer(clientConfig, httpAddress, http2SettingsHandler, http2ResponseHandler);
        }
        return bootstrap.handler(initializer)
                .connect(httpAddress.getInetSocketAddress()).sync().await().channel();
    }

    /**
     * For following redirects by a chain of transports.
     * @param transport the previous transport
     * @param request the new request for continuing the request.
     */
    public void continuation(Transport transport, Request request) {
        Transport nextTransport = newTransport(HttpAddress.of(request));
        nextTransport.setResponseListener(transport.getResponseListener());
        nextTransport.setExceptionListener(transport.getExceptionListener());
        nextTransport.setHeadersListener(transport.getHeadersListener());
        nextTransport.setCookieListener(transport.getCookieListener());
        nextTransport.setPushListener(transport.getPushListener());
        nextTransport.setCookieBox(transport.getCookieBox());
        nextTransport.execute(request);
        nextTransport.get();
        close(nextTransport);
    }

    public Transport execute(Request request) {
        Transport nextTransport = newTransport(HttpAddress.of(request));
        nextTransport.execute(request);
        return nextTransport;
    }

    public <T> CompletableFuture<T> execute(Request request,
                                            Function<FullHttpResponse, T> supplier) {
        return newTransport(HttpAddress.of(request)).execute(request, supplier);
    }

    public Transport prepareRequest(Request request) {
        return newTransport(HttpAddress.of(request));
    }

    public void close(Transport transport) {
        if (transportListener != null) {
            transportListener.onClose(transport);
        }
        transport.close();
        transports.remove(transport);
    }

    public void close() {
        for (Transport transport : transports) {
            close(transport);
        }
    }

    public void shutdown() {
        eventLoopGroup.shutdownGracefully();
    }

    public void shutdownGracefully() {
        close();
        shutdown();
    }

    public interface TransportListener {

        void onOpen(Transport transport);

        void onClose(Transport transport);
    }

    static class HttpClientThreadFactory implements ThreadFactory {

        private int number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-client-pool-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }
}
