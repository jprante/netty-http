package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.Future;
import org.xbib.netty.http.client.api.ClientProtocolProvider;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ClientTransport;
import org.xbib.netty.http.client.pool.BoundedChannelPool;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpChannelInitializer;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.common.NetworkUtils;
import org.xbib.netty.http.common.TransportProvider;
import org.xbib.netty.http.common.security.SecurityUtil;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.security.KeyStoreException;
import java.security.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Client implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(Client.class.getName());

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

    private final AtomicLong requestCounter;

    private final AtomicLong responseCounter;

    private final ClientConfig clientConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final Bootstrap bootstrap;

    private final Queue<ClientTransport> transports;

    private final List<ClientProtocolProvider<HttpChannelInitializer, ClientTransport>> protocolProviders;

    private final AtomicBoolean closed;

    private EventLoopGroup eventLoopGroup;

    private Class<? extends SocketChannel> socketChannelClass;

    private BoundedChannelPool<HttpAddress> pool;

    public Client() {
        this(new ClientConfig());
    }

    public Client(ClientConfig clientConfig) {
        this(clientConfig, null, null, null);
    }

    @SuppressWarnings("unchecked")
    public Client(ClientConfig clientConfig, ByteBufAllocator byteBufAllocator,
                  EventLoopGroup eventLoopGroup, Class<? extends SocketChannel> socketChannelClass) {
        Objects.requireNonNull(clientConfig);
        this.requestCounter = new AtomicLong();
        this.responseCounter = new AtomicLong();
        this.closed = new AtomicBoolean(false);
        this.clientConfig = clientConfig;
        this.protocolProviders = new ArrayList<>();
        for (ClientProtocolProvider<HttpChannelInitializer, ClientTransport> provider : ServiceLoader.load(ClientProtocolProvider.class)) {
            protocolProviders.add(provider);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "protocol provider: " + provider.transportClass());
            }
        }
        initializeTrustManagerFactory(clientConfig);
        this.byteBufAllocator = byteBufAllocator != null ? byteBufAllocator : ByteBufAllocator.DEFAULT;
        if (eventLoopGroup != null) {
            this.eventLoopGroup = eventLoopGroup;
        }
        if (socketChannelClass != null) {
            this.socketChannelClass = socketChannelClass;
        }
        ServiceLoader<TransportProvider> transportProviders = ServiceLoader.load(TransportProvider.class);
        for (TransportProvider transportProvider : transportProviders) {
            if (this.eventLoopGroup == null &&
                    (clientConfig.getTransportProviderName() == null || clientConfig.getTransportProviderName().equals(transportProvider.getClass().getName()))) {
                this.eventLoopGroup = transportProvider.createEventLoopGroup(clientConfig.getThreadCount(), new HttpClientThreadFactory());
            }
            if (this.socketChannelClass == null &&
                    (clientConfig.getTransportProviderName() == null || clientConfig.getTransportProviderName().equals(transportProvider.getClass().getName()))) {
                this.socketChannelClass = transportProvider.createSocketChannelClass();
            }
        }
        if (this.eventLoopGroup == null) {
            this.eventLoopGroup = new NioEventLoopGroup(clientConfig.getThreadCount(), new HttpClientThreadFactory());
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "event loop group class: " + this.eventLoopGroup.getClass().getName());
        }
        if (this.socketChannelClass == null) {
            this.socketChannelClass = NioSocketChannel.class;
        }
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "socket channel class: " + this.socketChannelClass.getName());
        }
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
                    clientConfig.getPoolKeySelectorType());
            Integer nodeConnectionLimit = clientConfig.getPoolNodeConnectionLimit();
            if (nodeConnectionLimit == null || nodeConnectionLimit == 0) {
                nodeConnectionLimit = nodes.size();
            }
            try {
                this.pool.prepare(nodeConnectionLimit);
            } catch (Exception e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
            logger.log(Level.FINE, "client pool prepared: size = " + nodeConnectionLimit);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ClientProtocolProvider<HttpChannelInitializer, ClientTransport>> getProtocolProviders() {
        return protocolProviders;
    }

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public ByteBufAllocator getByteBufAllocator() {
        return byteBufAllocator;
    }

    public boolean isPooled() {
        return pool != null;
    }

    public boolean hasPooledConnections() {
        return pool != null && !clientConfig.getPoolNodes().isEmpty();
    }

    public AtomicLong getRequestCounter() {
        return requestCounter;
    }

    public AtomicLong getResponseCounter() {
        return responseCounter;
    }

    public ClientTransport newTransport() {
        return newTransport(null);
    }

    public ClientTransport newTransport(HttpAddress httpAddress) {
        ClientTransport transport = null;
        if (httpAddress != null) {
            for (ClientProtocolProvider<HttpChannelInitializer, ClientTransport> protocolProvider : protocolProviders) {
                if (protocolProvider.supportsMajorVersion(httpAddress.getVersion().majorVersion())) {
                    try {
                        transport = protocolProvider.transportClass()
                                .getConstructor(Client.class, HttpAddress.class).newInstance(this, httpAddress);
                        break;
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        throw new IllegalStateException();
                    }
                }
            }
            if (transport == null) {
                throw new UnsupportedOperationException("no protocol support for " + httpAddress);
            }
        } else if (hasPooledConnections()) {
            for (ClientProtocolProvider<HttpChannelInitializer, ClientTransport> protocolProvider : protocolProviders) {
                if (protocolProvider.supportsMajorVersion(pool.getVersion().majorVersion())) {
                    try {
                        transport = protocolProvider.transportClass()
                                .getConstructor(Client.class, HttpAddress.class).newInstance(this, null);
                        break;
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                        throw new IllegalStateException();
                    }
                }
            }
            if (transport == null) {
                throw new UnsupportedOperationException("no pool protocol support for " + pool.getVersion().majorVersion());
            }
        } else {
            throw new IllegalStateException("no address given to connect to");
        }
        transports.add(transport);
        return transport;
    }

    public Channel newChannel(HttpAddress httpAddress) throws IOException {
        if (httpAddress != null) {
            HttpVersion httpVersion = httpAddress.getVersion();
            SslContext sslContext = newSslContext(clientConfig, httpAddress.getVersion());
            SslHandlerFactory sslHandlerFactory = new SslHandlerFactory(sslContext, clientConfig, httpAddress, byteBufAllocator);
            HttpChannelInitializer initializerTwo =
                    findChannelInitializer(2, httpAddress, sslHandlerFactory, null);
            HttpChannelInitializer initializer =
                    findChannelInitializer(httpVersion.majorVersion(), httpAddress, sslHandlerFactory, initializerTwo);
            try {
                return bootstrap.handler(initializer)
                        .connect(httpAddress.getInetSocketAddress()).sync().await().channel();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        } else {
            if (hasPooledConnections()) {
                try {
                    if (pool != null) {
                        return pool.acquire();
                    } else {
                        logger.log(Level.SEVERE, "no pool prsent");
                        return null;
                    }
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    public void releaseChannel(Channel channel, boolean close) throws IOException{
        if (channel == null) {
            return;
        }
        if (hasPooledConnections()) {
            try {
                if (pool != null) {
                    pool.release(channel, close);
                }
            } catch (Exception e) {
                throw new IOException(e);
            }
        } else if (close) {
           channel.close();
        }
    }

    public ClientTransport execute(Request request) throws IOException {
        return newTransport(HttpAddress.of(request.url(), request.httpVersion()))
                .execute(request);
    }

    /**
     * Execute a request and return a {@link CompletableFuture}.
     *
     * @param request the request
     * @param supplier the function for the response
     * @param <T> the result of the function for the response
     * @return the completable future
     * @throws IOException if the request fails to be executed.
     */
    public <T> CompletableFuture<T> execute(Request request,
                                            Function<HttpResponse, T> supplier) throws IOException {
        return newTransport(HttpAddress.of(request.url(), request.httpVersion()))
                .execute(request, supplier);
    }

    /**
     * For following redirects, construct a new transport.
     * @param transport the previous transport
     * @param request the new request for continuing the request.
     * @throws IOException if continuation fails
     */
    public void continuation(ClientTransport transport, Request request) throws IOException {
        ClientTransport nextTransport = newTransport(HttpAddress.of(request.url(), request.httpVersion()));
        nextTransport.setCookieBox(transport.getCookieBox());
        nextTransport.execute(request);
        nextTransport.get();
        closeAndRemove(nextTransport);
    }

    /**
     * Retry request.
     *
     * @param transport the transport to retry
     * @param request the request to retry
     * @throws IOException if retry failed
     */
    public void retry(ClientTransport transport, Request request) throws IOException {
        transport.execute(request);
        transport.get();
        closeAndRemove(transport);
    }

    @Override
    public void close() throws IOException {
        shutdownGracefully();
    }

    public void shutdownGracefully() throws IOException {
        shutdownGracefully(30L, TimeUnit.SECONDS);
    }

    public void shutdownGracefully(long amount, TimeUnit timeUnit) throws IOException {
        if (closed.compareAndSet(false, true)) {
            try {
                for (ClientTransport transport : transports) {
                    transport.close();
                }
                transports.clear();
                if (hasPooledConnections()) {
                    pool.close();
                }
                Future<?> future = eventLoopGroup.shutdownGracefully(1L, amount, timeUnit);
                eventLoopGroup.awaitTermination(amount, timeUnit);
                future.sync();
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
    }

    private void closeAndRemove(ClientTransport transport) throws IOException {
        try {
            transport.close();
        } catch (Exception e) {
            throw new IOException(e);
        } finally {
            transports.remove(transport);
        }
    }

    private HttpChannelInitializer findChannelInitializer(int majorVersion,
                                                          HttpAddress httpAddress,
                                                          SslHandlerFactory sslHandlerFactory,
                                                          HttpChannelInitializer helper) {
        for (ClientProtocolProvider<HttpChannelInitializer, ClientTransport> protocolProvider : protocolProviders) {
            if (protocolProvider.supportsMajorVersion(majorVersion)) {
                try {
                    return protocolProvider.initializerClass()
                            .getConstructor(ClientConfig.class, HttpAddress.class, SslHandlerFactory.class, HttpChannelInitializer.class)
                            .newInstance(clientConfig, httpAddress, sslHandlerFactory, helper);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new IllegalStateException();
                }
            }
        }
        throw new IllegalStateException("no channel initializer found for major version " + majorVersion);
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

    private static SslContext newSslContext(ClientConfig clientConfig, HttpVersion httpVersion) throws SSLException {
        // Conscrypt support?
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

        private long number = 0;

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
            SslHandlerFactory sslHandlerFactory = new SslHandlerFactory(sslContext,
                    clientConfig, httpAddress, byteBufAllocator);
            HttpChannelInitializer initializerTwo =
                    findChannelInitializer(2, httpAddress, sslHandlerFactory, null);
            HttpChannelInitializer initializer =
                    findChannelInitializer(httpVersion.majorVersion(), httpAddress, sslHandlerFactory, initializerTwo);
            initializer.initChannel(channel);
        }
    }

    public static class SslHandlerFactory {

        private final SslContext sslContext;

        private final ClientConfig clientConfig;

        private final HttpAddress httpAddress;

        private final ByteBufAllocator allocator;

        SslHandlerFactory(SslContext sslContext, ClientConfig clientConfig,
                          HttpAddress httpAddress, ByteBufAllocator allocator) {
            this.sslContext = sslContext;
            this.clientConfig = clientConfig;
            this.httpAddress = httpAddress;
            this.allocator = allocator;
        }

        public SslHandler create() {
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
            engine.setEnabledProtocols(clientConfig.getProtocols());
            return sslHandler;
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

        public Builder setTransportProviderName(String transportProviderName) {
            clientConfig.setTransportProviderName(transportProviderName);
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

        public Builder enableGzip(boolean enableGzip) {
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

        public Builder setTransportLayerSecurityProtocols(String... protocols) {
            clientConfig.setProtocols(protocols);
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
