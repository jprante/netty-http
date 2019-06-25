package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.xbib.netty.http.client.handler.http.HttpChannelInitializer;
import org.xbib.netty.http.client.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.client.pool.BoundedChannelPool;
import org.xbib.netty.http.client.transport.Http2Transport;
import org.xbib.netty.http.client.transport.HttpTransport;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.NetworkUtils;
import org.xbib.netty.http.common.SecurityUtil;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    }

    private final ClientConfig clientConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup eventLoopGroup;

    private final Class<? extends SocketChannel> socketChannelClass;

    private final Bootstrap bootstrap;

    private final Queue<Transport> transports;

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
                .option(ChannelOption.ALLOCATOR, byteBufAllocator)
                .option(ChannelOption.TCP_NODELAY, clientConfig.isTcpNodelay())
                .option(ChannelOption.SO_KEEPALIVE, clientConfig.isKeepAlive())
                .option(ChannelOption.SO_REUSEADDR, clientConfig.isReuseAddr())
                .option(ChannelOption.SO_SNDBUF, clientConfig.getTcpSendBufferSize())
                .option(ChannelOption.SO_RCVBUF, clientConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, clientConfig.getConnectTimeoutMillis())
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, clientConfig.getWriteBufferWaterMark());
        this.transports = new ConcurrentLinkedQueue<>();
        if (!clientConfig.getPoolNodes().isEmpty()) {
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
                    nodes, bootstrap, clientChannelPoolHandler, retries,
                    BoundedChannelPool.PoolKeySelectorType.ROUNDROBIN);
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

    public static Builder builder() {
        return new Builder();
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public ByteBufAllocator getByteBufAllocator() {
        return byteBufAllocator;
    }

    public boolean hasPooledConnections() {
        return pool != null && !clientConfig.getPoolNodes().isEmpty();
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "JDK ciphers: " + SecurityUtil.Defaults.JDK_CIPHERS);
        logger.log(level, () -> "OpenSSL ciphers: " + SecurityUtil.Defaults.OPENSSL_CIPHERS);
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable());
        logger.log(level, () -> "OpenSSL ALPN support: " + OpenSsl.isAlpnSupported());
        logger.log(level, () -> "Candidate ciphers on client: " + clientConfig.getCiphers());
        logger.log(level, () -> "Local host name: " + NetworkUtils.getLocalHostName("localhost"));
        logger.log(level, () -> "Event loop group: " + eventLoopGroup + " threads=" + clientConfig.getThreadCount());
        logger.log(level, () -> "Socket: " + socketChannelClass.getName());
        logger.log(level, () -> "Allocator: " + byteBufAllocator.getClass().getName());
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public Transport newTransport() {
        return newTransport(null);
    }

    public Transport newTransport(HttpAddress httpAddress) {
        Transport transport;
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
        } else {
            throw new IllegalStateException("no address given to connect to");
        }
        transports.add(transport);
        return transport;
    }

    public Channel newChannel(HttpAddress httpAddress) throws IOException {
        Channel channel;
        if (httpAddress != null) {
            HttpVersion httpVersion = httpAddress.getVersion();
            SslContext sslContext = newSslContext(clientConfig, httpAddress.getVersion());
            SslHandlerFactory sslHandlerFactory = new SslHandlerFactory(sslContext, clientConfig, httpAddress, byteBufAllocator);
            ChannelInitializer<Channel> initializer;
            if (httpVersion.majorVersion() == 1) {
                initializer = new HttpChannelInitializer(clientConfig, httpAddress, sslHandlerFactory,
                        new Http2ChannelInitializer(clientConfig, httpAddress, sslHandlerFactory));
            } else {
                initializer = new Http2ChannelInitializer(clientConfig, httpAddress, sslHandlerFactory);
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

    public void releaseChannel(Channel channel, boolean close) throws IOException{
        if (channel == null) {
            return;
        }
        if (hasPooledConnections()) {
            try {
                pool.release(channel, close);
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else if (close) {
           channel.close();
        }
    }

    public Transport execute(Request request) throws IOException {
        return newTransport(HttpAddress.of(request.url(), request.httpVersion()))
                .execute(request);
    }

    public <T> CompletableFuture<T> execute(Request request,
                                            Function<FullHttpResponse, T> supplier) throws IOException {
        return newTransport(HttpAddress.of(request.url(), request.httpVersion()))
                .execute(request, supplier);
    }

    /**
     * For following redirects, construct a new transport.
     * @param transport the previous transport
     * @param request the new request for continuing the request.
     * @throws IOException if continuation fails
     */
    public void continuation(Transport transport, Request request) throws IOException {
        Transport nextTransport = newTransport(HttpAddress.of(request.url(), request.httpVersion()));
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

    public void shutdownGracefully() throws IOException {
        logger.log(Level.FINE, "shutting down gracefully");
        for (Transport transport : transports) {
            close(transport);
        }
        // how to wait for all responses for the pool?
        if (hasPooledConnections()) {
            pool.close();
        }
        logger.log(Level.FINE, "shutting down");
        eventLoopGroup.shutdownGracefully();
        try {
            eventLoopGroup.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private void close(Transport transport) throws IOException {
        transport.close();
        transports.remove(transport);
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

    private static SslHandler newSslHandler(SslContext sslContext,
                                            ClientConfig clientConfig, ByteBufAllocator allocator, HttpAddress httpAddress) {
        InetSocketAddress peer = httpAddress.getInetSocketAddress();
        SslHandler sslHandler = sslContext.newHandler(allocator, peer.getHostName(), peer.getPort());
        SSLEngine engine = sslHandler.engine();
        List<String> serverNames = clientConfig.getServerNamesForIdentification();
        if (serverNames.isEmpty()) {
            serverNames = Collections.singletonList(peer.getHostName());
        }
        SSLParameters params = engine.getSSLParameters();
        // use sslContext.newHandler(allocator, peerHost, peerPort) when using params.setEndpointIdentificationAlgorithm
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
    }

    private static SslContext newSslContext(ClientConfig clientConfig, HttpVersion httpVersion) throws SSLException {
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient()
                .sslProvider(clientConfig.getSslProvider())
                .ciphers(clientConfig.getCiphers(), clientConfig.getCipherSuiteFilter())
                .applicationProtocolConfig(newApplicationProtocolConfig(httpVersion));
        if (clientConfig.getSslContextProvider() != null) {
            sslContextBuilder.sslContextProvider(clientConfig.getSslContextProvider());
        }
        if (clientConfig.getTrustManagerFactory() != null) {
            sslContextBuilder.trustManager(clientConfig.getTrustManagerFactory());
        }
        return sslContextBuilder.build();
    }

    private static ApplicationProtocolConfig newApplicationProtocolConfig(HttpVersion httpVersion) {
        return httpVersion.majorVersion() == 1 ?
                new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_1_1) :
                new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2);
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

    private class ClientChannelPoolHandler implements ChannelPoolHandler {

        @Override
        public void channelReleased(Channel channel) {
        }

        @Override
        public void channelAcquired(Channel channel) {
        }

        @Override
        public void channelCreated(Channel channel) throws IOException {
            HttpAddress httpAddress = channel.attr(pool.getAttributeKey()).get();
            HttpVersion httpVersion = httpAddress.getVersion();
            SslContext sslContext = newSslContext(clientConfig, httpAddress.getVersion());
            SslHandlerFactory sslHandlerFactory = new SslHandlerFactory(sslContext, clientConfig, httpAddress, byteBufAllocator);
            Http2ChannelInitializer http2ChannelInitializer =
                    new Http2ChannelInitializer(clientConfig, httpAddress, sslHandlerFactory);
            if (httpVersion.majorVersion() == 1) {
                HttpChannelInitializer initializer =
                        new HttpChannelInitializer(clientConfig, httpAddress, sslHandlerFactory, http2ChannelInitializer);
                initializer.initChannel(channel);
            } else {
                http2ChannelInitializer.initChannel(channel);
            }
        }
    }

    public class SslHandlerFactory {

        private final SslContext sslContext;

        private final ClientConfig clientConfig;

        private final HttpAddress httpAddress;

        private final ByteBufAllocator allocator;

        SslHandlerFactory(SslContext sslContext, ClientConfig clientConfig, HttpAddress httpAddress, ByteBufAllocator allocator) {
            this.sslContext = sslContext;
            this.clientConfig = clientConfig;
            this.httpAddress = httpAddress;
            this.allocator = allocator;
        }

        public SslHandler create() {
            return newSslHandler(sslContext, clientConfig, allocator, httpAddress);
        }
    }

    public static class Builder {

        private ByteBufAllocator byteBufAllocator;

        private EventLoopGroup eventLoopGroup;

        private Class<? extends SocketChannel> socketChannelClass;

        private ClientConfig clientConfig;

        private Builder() {
            this.clientConfig = new ClientConfig();
        }

        public Builder enableDebug() {
            clientConfig.enableDebug();
            return this;
        }

        public Builder disableDebug() {
            clientConfig.disableDebug();
            return this;
        }

        /**
         * Set byte buf allocator for payload in HTTP requests.
         * @param byteBufAllocator the byte buf allocator
         * @return this builder
         */
        public Builder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
            this.byteBufAllocator = byteBufAllocator;
            return this;
        }

        public Builder setEventLoop(EventLoopGroup eventLoopGroup) {
            this.eventLoopGroup = eventLoopGroup;
            return this;
        }

        public Builder setChannelClass(Class<SocketChannel> socketChannelClass) {
            this.socketChannelClass = socketChannelClass;
            return this;
        }

        public Builder setThreadCount(int threadCount) {
            clientConfig.setThreadCount(threadCount);
            return this;
        }

        public Builder setConnectTimeoutMillis(int connectTimeoutMillis) {
            clientConfig.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        public Builder setTcpSendBufferSize(int tcpSendBufferSize) {
            clientConfig.setTcpSendBufferSize(tcpSendBufferSize);
            return this;
        }

        public Builder setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
            clientConfig.setTcpReceiveBufferSize(tcpReceiveBufferSize);
            return this;
        }

        public Builder setTcpNodelay(boolean tcpNodelay) {
            clientConfig.setTcpNodelay(tcpNodelay);
            return this;
        }

        public Builder setKeepAlive(boolean keepAlive) {
            clientConfig.setKeepAlive(keepAlive);
            return this;
        }

        public Builder setReuseAddr(boolean reuseAddr) {
            clientConfig.setReuseAddr(reuseAddr);
            return this;
        }

        public Builder setMaxChunkSize(int maxChunkSize) {
            clientConfig.setMaxChunkSize(maxChunkSize);
            return this;
        }

        public Builder setMaxInitialLineLength(int maxInitialLineLength) {
            clientConfig.setMaxInitialLineLength(maxInitialLineLength);
            return this;
        }

        public Builder setMaxHeadersSize(int maxHeadersSize) {
            clientConfig.setMaxHeadersSize(maxHeadersSize);
            return this;
        }

        public Builder setMaxContentLength(int maxContentLength) {
            clientConfig.setMaxContentLength(maxContentLength);
            return this;
        }

        public Builder setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
            clientConfig.setMaxCompositeBufferComponents(maxCompositeBufferComponents);
            return this;
        }

        public Builder setReadTimeoutMillis(int readTimeoutMillis) {
            clientConfig.setReadTimeoutMillis(readTimeoutMillis);
            return this;
        }

        public Builder setEnableGzip(boolean enableGzip) {
            clientConfig.setEnableGzip(enableGzip);
            return this;
        }

        public Builder setSslProvider(SslProvider sslProvider) {
            clientConfig.setSslProvider(sslProvider);
            return this;
        }

        public Builder setJdkSslProvider() {
            clientConfig.setJdkSslProvider();
            clientConfig.setCiphers(SecurityUtil.Defaults.JDK_CIPHERS);
            return this;
        }

        public Builder setOpenSSLSslProvider() {
            clientConfig.setOpenSSLSslProvider();
            clientConfig.setCiphers(SecurityUtil.Defaults.OPENSSL_CIPHERS);
            return this;
        }

        public Builder setSslContextProvider(Provider provider) {
            clientConfig.setSslContextProvider(provider);
            return this;
        }

        public Builder setCiphers(Iterable<String> ciphers) {
            clientConfig.setCiphers(ciphers);
            return this;
        }

        public Builder setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
            clientConfig.setCipherSuiteFilter(cipherSuiteFilter);
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
            clientConfig.setKeyCert(keyCertChainInputStream, keyInputStream);
            return this;
        }

        public Builder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                  String keyPassword) {
            clientConfig.setKeyCert(keyCertChainInputStream, keyInputStream, keyPassword);
            return this;
        }

        public Builder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
            clientConfig.setTrustManagerFactory(trustManagerFactory);
            return this;
        }

        public Builder trustInsecure() {
            clientConfig.setTrustManagerFactory(InsecureTrustManagerFactory.INSTANCE);
            return this;
        }

        public Builder setClientAuthMode(ClientAuthMode clientAuthMode) {
            clientConfig.setClientAuthMode(clientAuthMode);
            return this;
        }

        public Builder setHttpProxyHandler(HttpProxyHandler httpProxyHandler) {
            clientConfig.setHttpProxyHandler(httpProxyHandler);
            return this;
        }

        public Builder addPoolNode(HttpAddress httpAddress) {
            clientConfig.addPoolNode(httpAddress);
            clientConfig.setPoolVersion(httpAddress.getVersion());
            clientConfig.setPoolSecure(httpAddress.isSecure());
            return this;
        }

        public Builder setPoolNodeConnectionLimit(int nodeConnectionLimit) {
            clientConfig.setPoolNodeConnectionLimit(nodeConnectionLimit);
            return this;
        }

        public Builder setRetriesPerPoolNode(int retriesPerNode) {
            clientConfig.setRetriesPerPoolNode(retriesPerNode);
            return this;
        }

        public Builder addServerNameForIdentification(String serverName) {
            clientConfig.addServerNameForIdentification(serverName);
            return this;
        }

        public Builder setHttp2Settings(Http2Settings http2Settings) {
            clientConfig.setHttp2Settings(http2Settings);
            return this;
        }

        public Builder setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            clientConfig.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        public Builder enableNegotiation(boolean enableNegotiation) {
            clientConfig.setEnableNegotiation(enableNegotiation);
            return this;
        }

        public Client build() {
            return new Client(clientConfig, byteBufAllocator, eventLoopGroup, socketChannelClass);
        }
    }
}
