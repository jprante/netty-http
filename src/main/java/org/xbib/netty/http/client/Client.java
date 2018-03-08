package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.xbib.net.URL;
import org.xbib.netty.http.client.handler.http1.HttpChannelInitializer;
import org.xbib.netty.http.client.handler.http1.HttpResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.client.handler.http2.Http2ResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2SettingsHandler;
import org.xbib.netty.http.client.pool.BoundedChannelPool;
import org.xbib.netty.http.client.transport.Http2Transport;
import org.xbib.netty.http.client.transport.HttpTransport;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.client.util.NetworkUtils;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Client {

    private static final Logger logger = Logger.getLogger(Client.class.getName());

    private static final ThreadFactory httpClientThreadFactory = new HttpClientThreadFactory();

    static {
        if (System.getProperty("xbib.netty.http.client.extendsystemproperties") != null) {
            NetworkUtils.extendSystemProperties();
        }
        // change Netty defaults to safer ones, but still allow override from arg line
        if (System.getProperty("io.netty.noUnsafe") == null) {
            System.setProperty("io.netty.noUnsafe", Boolean.toString(true));
        }
        if (System.getProperty("io.netty.noKeySetOptimization") == null) {
            System.setProperty("io.netty.noKeySetOptimization", Boolean.toString(true));
        }
        if (System.getProperty("io.netty.recycler.maxCapacity") == null) {
            System.setProperty("io.netty.recycler.maxCapacity", Integer.toString(0));
        }
        if (System.getProperty("io.netty.leakDetection.level") == null) {
            System.setProperty("io.netty.leakDetection.level", "paranoid");
        }
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

    private BoundedChannelPool<HttpAddress> pool;

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
        initializeTrustManagerFactory(clientConfig);
        this.byteBufAllocator = byteBufAllocator != null ?
                byteBufAllocator : ByteBufAllocator.DEFAULT;
        this.eventLoopGroup = eventLoopGroup != null ? eventLoopGroup : clientConfig.isEpoll() ?
                    new EpollEventLoopGroup(clientConfig.getThreadCount(), httpClientThreadFactory) :
                    new NioEventLoopGroup(clientConfig.getThreadCount(), httpClientThreadFactory);
        this.socketChannelClass = socketChannelClass != null ? socketChannelClass : clientConfig.isEpoll() ?
                EpollSocketChannel.class : NioSocketChannel.class;
        this.bootstrap = new Bootstrap()
                .group(this.eventLoopGroup)
                .channel(this.socketChannelClass)
                //.option(ChannelOption.ALLOCATOR, byteBufAllocator)
                .option(ChannelOption.TCP_NODELAY, clientConfig.isTcpNodelay())
                .option(ChannelOption.SO_KEEPALIVE, clientConfig.isKeepAlive())
                .option(ChannelOption.SO_REUSEADDR, clientConfig.isReuseAddr())
                .option(ChannelOption.SO_SNDBUF, clientConfig.getTcpSendBufferSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, clientConfig.getWriteBufferWaterMark());
        this.httpResponseHandler = new HttpResponseHandler();
        this.http2SettingsHandler = new Http2SettingsHandler();
        this.http2ResponseHandler = new Http2ResponseHandler();
        this.transports = new CopyOnWriteArrayList<>();
        if (hasPooledConnections()) {
            List<HttpAddress> nodes = clientConfig.getPoolNodes();
            Integer limit = clientConfig.getPoolNodeConnectionLimit();
            if (limit == null || limit < 1) {
                limit = 1;
            }
            Semaphore semaphore = new Semaphore(limit);
            Integer retries = clientConfig.getRetriesPerPoolNode();
            if (retries == null || retries < 0) {
                retries = 0;
            }
            ClientChannelPoolHandler clientChannelPoolHandler = new ClientChannelPoolHandler();
            this.pool = new BoundedChannelPool<>(semaphore, clientConfig.getPoolVersion(),
                    clientConfig.isPoolSecure(), nodes, bootstrap, clientChannelPoolHandler, retries);
            Integer nodeConnectionLimit = clientConfig.getPoolNodeConnectionLimit();
            if (nodeConnectionLimit == null || nodeConnectionLimit == 0) {
                nodeConnectionLimit = nodes.size();
            }
            try {
                this.pool.prepare(nodeConnectionLimit);
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
        }
    }

    public static ClientBuilder builder() {
        return new ClientBuilder();
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public ByteBufAllocator getByteBufAllocator() {
        return byteBufAllocator;
    }

    public EventLoopGroup getEventLoopGroup() {
        return eventLoopGroup;
    }

    public void setTransportListener(TransportListener transportListener) {
        this.transportListener = transportListener;
    }

    public boolean hasPooledConnections() {
        return !clientConfig.getPoolNodes().isEmpty();
    }

    public BoundedChannelPool<HttpAddress> getPool() {
        return pool;
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable() +
                " OpenSSL ALPN support: " + OpenSsl.isAlpnSupported() +
                " Local host name: " + NetworkUtils.getLocalHostName("localhost") +
                " event loop group: " + eventLoopGroup +
                " socket: " + socketChannelClass.getName() +
                " allocator: " + byteBufAllocator.getClass().getName());
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public Transport newTransport() {
        return newTransport(null);
    }

    public Transport newTransport(URL url, HttpVersion httpVersion) {
        return newTransport(HttpAddress.of(url, httpVersion));
    }

    public Transport newTransport(HttpAddress httpAddress) {
        Transport transport = null;
        if (httpAddress != null) {
            if (httpAddress.getVersion().majorVersion() == 1) {
                transport = new HttpTransport(this, httpAddress);
            } else {
                transport = new Http2Transport(this, httpAddress);
            }
        } else if (hasPooledConnections()) {
            if (pool.getVersion().majorVersion() == 1) {
                transport = new HttpTransport(this, null);
            } else {
                transport = new Http2Transport(this, null);
            }
        }
        if (transport != null) {
            if (transportListener != null) {
                transportListener.onOpen(transport);
            }
            transports.add(transport);
        }
        return transport;
    }

    public Channel newChannel(HttpAddress httpAddress) throws IOException {
        Channel channel = null;
        if (httpAddress != null) {
            HttpVersion httpVersion = httpAddress.getVersion();
            ChannelInitializer<SocketChannel> initializer;
            SslHandler sslHandler = newSslHandler(clientConfig, byteBufAllocator, httpAddress);
            if (httpVersion.majorVersion() == 1) {
                initializer = new HttpChannelInitializer(clientConfig, httpAddress,
                        sslHandler, httpResponseHandler);
            } else {
                initializer = new Http2ChannelInitializer(clientConfig, httpAddress,
                        sslHandler, http2SettingsHandler, http2ResponseHandler);
            }
            try {
                channel = bootstrap.handler(initializer)
                        .connect(httpAddress.getInetSocketAddress()).sync().await().channel();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        } else {
            if (hasPooledConnections()) {
                try {
                    channel = pool.acquire();
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
        return channel;
    }

    public Channel newChannel() throws IOException {
        return newChannel(null);
    }

    public void releaseChannel(Channel channel) throws IOException{
        if (hasPooledConnections()) {
            try {
                pool.release(channel);
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else {
            if (channel != null) {
                channel.close();
            }
        }
    }

    public Transport execute(Request request) throws IOException {
        Transport transport = newTransport(HttpAddress.of(request));
        transport.execute(request);
        return transport;
    }

    public <T> CompletableFuture<T> execute(Request request,
                                            Function<FullHttpResponse, T> supplier) throws IOException {
        return newTransport(HttpAddress.of(request)).execute(request, supplier);
    }

    public Transport pooledExecute(Request request) throws IOException {
        Transport transport = newTransport();
        transport.execute(request);
        return transport;
    }

    /**
     * For following redirects, construct a new transport.
     * @param transport the previous transport
     * @param request the new request for continuing the request.
     */
    public void continuation(Transport transport, Request request) throws IOException {
        Transport nextTransport = newTransport(HttpAddress.of(request));
        nextTransport.setCookieBox(transport.getCookieBox());
        nextTransport.execute(request);
        nextTransport.get();
        close(nextTransport);
    }

    /**
     * Retry request by following a back-off strategy.
     *
     * @param transport the transport to retry
     * @param request the request to retry
     * @throws IOException if retry failed
     */
    public void retry(Transport transport, Request request) throws IOException {
        transport.execute(request);
        transport.get();
        close(transport);
    }

    public Transport prepareRequest(Request request) {
        return newTransport(HttpAddress.of(request));
    }

    public void close(Transport transport) throws IOException {
        if (transportListener != null) {
            transportListener.onClose(transport);
        }
        transport.close();
        transports.remove(transport);
    }

    public void close() throws IOException {
        for (Transport transport : transports) {
            close(transport);
        }
    }

    public void shutdownGracefully() throws IOException {
        if (hasPooledConnections()) {
            pool.close();
        }
        close();
        shutdown();
    }

    public void shutdown() {
        eventLoopGroup.shutdownGracefully();
        try {
            eventLoopGroup.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    /**
     * Initialize trust manager factory once per client lifecycle.
     * @param clientConfig the client config
     */
    private static void initializeTrustManagerFactory(ClientConfig clientConfig) {
        TrustManagerFactory trustManagerFactory = clientConfig.getTrustManagerFactory();
        if (trustManagerFactory != null) {
            try {
                trustManagerFactory.init(clientConfig.getTrustManagerKeyStore());
            } catch (KeyStoreException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    private static SslHandler newSslHandler(ClientConfig clientConfig, ByteBufAllocator allocator, HttpAddress httpAddress) {
        try {
            SslContext sslContext = newSslContext(clientConfig);
            SslHandler sslHandler = sslContext.newHandler(allocator);
            SSLEngine engine = sslHandler.engine();
            List<String> serverNames = clientConfig.getServerNamesForIdentification();
            if (serverNames.isEmpty()) {
                serverNames = Collections.singletonList(httpAddress.getInetSocketAddress().getHostName());
            }
            SSLParameters params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            List<SNIServerName> sniServerNames = new ArrayList<>();
            for (String serverName : serverNames) {
                sniServerNames.add(new SNIHostName(serverName));
            }
            params.setServerNames(sniServerNames);
            engine.setSSLParameters(params);
            switch (clientConfig.getClientAuthMode()) {
                case NEED:
                    engine.setNeedClientAuth(true);
                    break;
                case WANT:
                    engine.setWantClientAuth(true);
                    break;
                default:
                    break;
            }
            return sslHandler;
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static SslContext newSslContext(ClientConfig clientConfig) throws SSLException {
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                .sslProvider(clientConfig.getSslProvider())
                .ciphers(Http2SecurityUtil.CIPHERS, clientConfig.getCipherSuiteFilter())
                .applicationProtocolConfig(newApplicationProtocolConfig());
        if (clientConfig.getSslContextProvider() != null) {
            sslContextBuilder.sslContextProvider(clientConfig.getSslContextProvider());
        }
        if (clientConfig.getTrustManagerFactory() != null) {
            sslContextBuilder.trustManager(clientConfig.getTrustManagerFactory());
        }
        return sslContextBuilder.build();
    }

    private static ApplicationProtocolConfig newApplicationProtocolConfig() {
        return new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2);
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

    class ClientChannelPoolHandler implements ChannelPoolHandler {

        @Override
        public void channelReleased(Channel channel) {
        }

        @Override
        public void channelAcquired(Channel channel) {
        }

        @Override
        public void channelCreated(Channel channel) {
            HttpAddress httpAddress = channel.attr(pool.getAttributeKey()).get();
            HttpVersion httpVersion = httpAddress.getVersion();
            SslHandler sslHandler = newSslHandler(clientConfig, byteBufAllocator, httpAddress);
            if (httpVersion.majorVersion() == 1) {
                HttpChannelInitializer initializer = new HttpChannelInitializer(clientConfig, httpAddress,
                        sslHandler, httpResponseHandler);
                if (channel instanceof SocketChannel) {
                    initializer.initChannel((SocketChannel) channel);
                }
            } else {
                Http2ChannelInitializer initializer = new Http2ChannelInitializer(clientConfig, httpAddress,
                        sslHandler, http2SettingsHandler, http2ResponseHandler);
                if (channel instanceof SocketChannel) {
                    initializer.initChannel((SocketChannel) channel);
                }
            }
        }
    }
}
