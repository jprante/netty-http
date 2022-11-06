package org.xbib.netty.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainWildcardMappingBuilder;
import io.netty.util.Mapping;
import org.xbib.net.URL;
import org.xbib.net.URLBuilder;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpChannelInitializer;
import org.xbib.netty.http.common.TransportProvider;
import org.xbib.netty.http.server.api.Domain;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.ServerConfig;
import org.xbib.netty.http.server.api.ServerProtocolProvider;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.api.ServerTransport;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.security.CertificateUtils;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.BindException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server.
 */
public final class Server implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(Server.class.getName());

    static {
        // change Netty defaults to safer ones, but still allow override from arg line
        if (System.getProperty("io.netty.noUnsafe") == null) {
            System.setProperty("io.netty.noUnsafe", Boolean.toString(true));
        }
        if (System.getProperty("io.netty.noKeySetOptimization") == null) {
            System.setProperty("io.netty.noKeySetOptimization", Boolean.toString(true));
        }
    }

    private final ServerConfig serverConfig;

    private final EventLoopGroup parentEventLoopGroup;

    private final EventLoopGroup childEventLoopGroup;

    /**
     * An extra thread pool for handling requests. May be null
     * for executing request on the Netty event pool threads.
     */
    private final BlockingThreadPoolExecutor executor;

    private final ServerBootstrap bootstrap;

    private ChannelFuture channelFuture;

    private final List<ServerProtocolProvider<HttpChannelInitializer, ServerTransport>> protocolProviders;

    private static final AtomicLong requestCounter = new AtomicLong();

    private static final AtomicLong responseCounter = new AtomicLong();

    /**
     * Create a new HTTP server.
     *
     * @param serverConfig server configuration
     * @param byteBufAllocator byte buf allocator
     * @param parentEventLoopGroup parent event loop group
     * @param childEventLoopGroup child event loop group
     * @param socketChannelClass socket channel class
     * @param executor an extra blocking thread pool executor or null
     */
    @SuppressWarnings("unchecked")
    private Server(ServerConfig serverConfig,
                   ByteBufAllocator byteBufAllocator,
                   EventLoopGroup parentEventLoopGroup,
                   EventLoopGroup childEventLoopGroup,
                   Class<? extends ServerSocketChannel> socketChannelClass,
                   BlockingThreadPoolExecutor executor) {
        Objects.requireNonNull(serverConfig);
        this.serverConfig = serverConfig;
        ByteBufAllocator byteBufAllocator1 = byteBufAllocator != null ? byteBufAllocator : ByteBufAllocator.DEFAULT;
        this.parentEventLoopGroup = createParentEventLoopGroup(serverConfig, parentEventLoopGroup);
        this.childEventLoopGroup = createChildEventLoopGroup(serverConfig, childEventLoopGroup);
        Class<? extends ServerSocketChannel> socketChannelClass1 = createSocketChannelClass(serverConfig, socketChannelClass);
        this.executor = executor;
        this.protocolProviders =new ArrayList<>();
        for (ServerProtocolProvider<HttpChannelInitializer, ServerTransport> provider : ServiceLoader.load(ServerProtocolProvider.class)) {
            protocolProviders.add(provider);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "protocol provider up: " + provider.transportClass());
            }
        }
        this.bootstrap = new ServerBootstrap()
                .group(this.parentEventLoopGroup, this.childEventLoopGroup)
                .channel(socketChannelClass1)
                .option(ChannelOption.ALLOCATOR, byteBufAllocator1)
                .option(ChannelOption.SO_REUSEADDR, serverConfig.isReuseAddr())
                .option(ChannelOption.SO_RCVBUF, serverConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.SO_BACKLOG, serverConfig.getBackLogSize())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverConfig.getConnectTimeoutMillis())
                .childOption(ChannelOption.ALLOCATOR, byteBufAllocator1)
                .childOption(ChannelOption.SO_REUSEADDR, serverConfig.isReuseAddr())
                .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNodelay())
                .childOption(ChannelOption.SO_SNDBUF, serverConfig.getTcpSendBufferSize())
                .childOption(ChannelOption.SO_RCVBUF, serverConfig.getTcpReceiveBufferSize())
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverConfig.getConnectTimeoutMillis())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, serverConfig.getWriteBufferWaterMark());
        if (serverConfig.isDebug()) {
            bootstrap.handler(new LoggingHandler("bootstrap-server", serverConfig.getTrafficDebugLogLevel()));
        }
        if (serverConfig.getDefaultDomain() == null) {
            throw new IllegalStateException("no default domain configured, unable to continue");
        }
        // translate domains into Netty mapping
        Mapping<String, SslContext> domainNameMapping = null;
        Domain<? extends EndpointResolver<?>> defaultDomain = serverConfig.getDefaultDomain();
        if (serverConfig.getAddress().isSecure() &&
                defaultDomain != null &&
                defaultDomain.getSslContext() != null) {
            DomainWildcardMappingBuilder<SslContext> mappingBuilder =
                    new DomainWildcardMappingBuilder<>(defaultDomain.getSslContext());
            for (Domain<? extends EndpointResolver<?>> domain : serverConfig.getDomains()) {
                if (!domain.getName().equals(defaultDomain.getName())) {
                    mappingBuilder.add(domain.getName(), domain.getSslContext());
                }
            }
            domainNameMapping = mappingBuilder.build();
            logger.log(Level.INFO, "domain name mapping: " + domainNameMapping);
        }
        bootstrap.childHandler(findChannelInitializer(serverConfig.getAddress().getVersion().majorVersion(),
                serverConfig.getAddress(), domainNameMapping));
    }

    public void loop() throws IOException, InterruptedException {
        accept().channel().closeFuture().sync();
    }

    @Override
    public void close() {
        try {
            shutdownGracefully();
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public static Builder builder(HttpServerDomain httpServerDomain) {
        return new Builder(httpServerDomain);
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /**
     * Start accepting incoming connections.
     * @return the channel future
     * @throws BindException if socket bind did not succeed
     */
    public ChannelFuture accept() throws BindException {
        try {
            HttpAddress httpAddress = serverConfig.getAddress();
            logger.log(Level.INFO, () -> "trying to bind to " + httpAddress);
            try {
                this.channelFuture = bootstrap.bind(httpAddress.getInetSocketAddress()).await().sync();
            } catch (InterruptedException e) {
                throw new BindException(e.getMessage());
            }
            logger.log(Level.INFO, () -> ServerName.getServerName() + " ready, listening on " + httpAddress);
            return channelFuture;
        } catch (Exception e) {
            if (e instanceof BindException) {
                throw e;
            } else {
                throw new BindException(e.getMessage());
            }
        }
    }

    public AtomicLong getRequestCounter() {
        return requestCounter;
    }

    public AtomicLong getResponseCounter() {
        return responseCounter;
    }

    public URL getBaseURL(HttpHeaders headers) {
        String scheme;
        String host;
        String port;
        if (headers == null) {
            URL bindURL = serverConfig.getDefaultDomain().getHttpAddress().base();
            scheme = bindURL.getScheme();
            host = bindURL.getHost();
            port = bindURL.getPort() != null ? Integer.toString(bindURL.getPort()) : null;
        } else if (headers.get("host") != null) {
            // proxy proto, host
            scheme = headers.get("x-forwarded-proto");
            if (scheme == null) {
                scheme = "http";
            }
            host = headers.get("x-forwarded-host");
            if (host == null) {
                host = headers.get("host");
            }
            port = extractPort(host);
            host = stripPort(host);
        } else {
            throw new IllegalArgumentException("no host header in " + headers);
        }
        URLBuilder builder = URL.builder().scheme(scheme).host(host);
        if (port != null) {
            builder.port(Integer.parseInt(port));
        }
        return builder.build();
    }

    /**
     * Returns the domain of the given URL.
     * @param url the URL
     * @return the domain
     */
    public Domain<? extends EndpointResolver<?>> getDomain(URL url) {
        return getDomain(hostAndPort(url));
    }

    /**
     * Returns the domain for the given host name.
     *
     * @param name the name of the virtual host with optional port, or null for the
     *             default domain
     * @return the virtual host with the given name or the default domain
     */
    public Domain<? extends EndpointResolver<?>> getDomain(String name) {
        return serverConfig.getDomain(name);
    }

    public void handle(ServerRequest.Builder serverRequestBuilder,
                       ServerResponse.Builder serverResponseBuilder) {
        URL baseURL = getBaseURL(serverRequestBuilder.getHeaders());
        serverRequestBuilder.setBaseURL(baseURL);
        Domain<? extends EndpointResolver<?>> domain = getDomain(baseURL);
        if (executor != null) {
            executor.submit(() -> {
                try {
                    domain.handle(serverRequestBuilder, serverResponseBuilder);
                } catch (Throwable t) {
                    executor.afterExecute(null, t);
                    domain.handleAfterError(serverRequestBuilder, serverResponseBuilder, t);
                } finally {
                    serverRequestBuilder.release();
                }
            });
        } else {
            try {
                domain.handle(serverRequestBuilder, serverResponseBuilder);
            } catch (Throwable t) {
                domain.handleAfterError(serverRequestBuilder, serverResponseBuilder, t);
            } finally {
                serverRequestBuilder.release();
            }
        }
    }

    public ServerTransport newTransport(HttpVersion httpVersion) {
        for (ServerProtocolProvider<HttpChannelInitializer, ServerTransport> protocolProvider : protocolProviders) {
            if (protocolProvider.supportsMajorVersion(httpVersion.majorVersion())) {
                try {
                    return protocolProvider.transportClass()
                            .getConstructor(Server.class)
                            .newInstance(this);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new IllegalStateException();
                }
            }
        }
        throw new IllegalStateException("no channel initializer found for major version " + httpVersion.majorVersion());
    }

    public void shutdownGracefully() throws IOException {
        shutdownGracefully(30L, TimeUnit.SECONDS);
    }

    public void shutdownGracefully(long amount, TimeUnit timeUnit) throws IOException {
        logger.log(Level.FINE, "shutting down");
        // first, shut down threads, then server socket
        childEventLoopGroup.shutdownGracefully(1L, amount, timeUnit);
        try {
            childEventLoopGroup.awaitTermination(amount, timeUnit);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        parentEventLoopGroup.shutdownGracefully(1L, amount, timeUnit);
        try {
            parentEventLoopGroup.awaitTermination(amount, timeUnit);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        try {
            if (channelFuture != null) {
                // close channel and wait for unbind
                channelFuture.channel().closeFuture().sync();
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static String stripPort(String hostMaybePort) {
        if (hostMaybePort == null) {
            return null;
        }
        int i = hostMaybePort.lastIndexOf(':');
        return i >= 0 ? hostMaybePort.substring(0, i) : hostMaybePort;
    }

    private static String extractPort(String hostMaybePort) {
        if (hostMaybePort == null) {
            return null;
        }
        int i = hostMaybePort.lastIndexOf(':');
        return i >= 0 ? hostMaybePort.substring(i + 1) : null;
    }

    private static String hostAndPort(URL url) {
        return url == null ? null : url.getPort() != null && url.getPort() != -1 ? url.getHost() + ":" + url.getPort() : url.getHost();
    }

    private HttpChannelInitializer findChannelInitializer(int majorVersion,
                                                          HttpAddress httpAddress,
                                                          Mapping<String, SslContext> domainNameMapping) {
        for (ServerProtocolProvider<HttpChannelInitializer, ServerTransport> protocolProvider : protocolProviders) {
            if (protocolProvider.supportsMajorVersion(majorVersion)) {
                try {
                    return protocolProvider.initializerClass()
                            .getConstructor(Server.class, HttpAddress.class, Mapping.class)
                            .newInstance(this, httpAddress, domainNameMapping);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new IllegalStateException();
                }
            }
        }
        throw new IllegalStateException("no channel initializer found for major version " + majorVersion);
    }

    private static EventLoopGroup createParentEventLoopGroup(ServerConfig serverConfig,
                                                             EventLoopGroup parentEventLoopGroup ) {
        EventLoopGroup eventLoopGroup = parentEventLoopGroup;
        if (eventLoopGroup == null) {
            ServiceLoader<TransportProvider> transportProviders = ServiceLoader.load(TransportProvider.class);
            for (TransportProvider transportProvider : transportProviders) {
                if (serverConfig.getTransportProviderName() == null || serverConfig.getTransportProviderName().equals(transportProvider.getClass().getName())) {
                    eventLoopGroup = transportProvider.createEventLoopGroup(serverConfig.getParentThreadCount(), new HttpServerParentThreadFactory());
                }
            }
        }
        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup(serverConfig.getParentThreadCount(), new HttpServerParentThreadFactory());
        }
        logger.log(Level.INFO, "parent event loop group = " + eventLoopGroup);
        return eventLoopGroup;
    }

    private static EventLoopGroup createChildEventLoopGroup(ServerConfig serverConfig,
                                                            EventLoopGroup childEventLoopGroup ) {
        EventLoopGroup eventLoopGroup = childEventLoopGroup;
        if (eventLoopGroup == null) {
            ServiceLoader<TransportProvider> transportProviders = ServiceLoader.load(TransportProvider.class);
            for (TransportProvider transportProvider : transportProviders) {
                if (serverConfig.getTransportProviderName() == null || serverConfig.getTransportProviderName().equals(transportProvider.getClass().getName())) {
                    eventLoopGroup = transportProvider.createEventLoopGroup(serverConfig.getChildThreadCount(), new HttpServerChildThreadFactory());
                }
            }
        }
        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup(serverConfig.getChildThreadCount(), new HttpServerChildThreadFactory());
        }
        logger.log(Level.INFO, "child event loop group = " + eventLoopGroup);
        return eventLoopGroup;
    }

    private static Class<? extends ServerSocketChannel> createSocketChannelClass(ServerConfig serverConfig,
                                                                                 Class<? extends ServerSocketChannel> socketChannelClass) {
        Class<? extends ServerSocketChannel> channelClass = socketChannelClass;
        if (channelClass == null) {
            ServiceLoader<TransportProvider> transportProviders = ServiceLoader.load(TransportProvider.class);
            for (TransportProvider transportProvider : transportProviders) {
                if (serverConfig.getTransportProviderName() == null || serverConfig.getTransportProviderName().equals(transportProvider.getClass().getName())) {
                    channelClass = transportProvider.createServerSocketChannelClass();
                }
            }
        }
        if (channelClass == null) {
            channelClass = NioServerSocketChannel.class;
        }
        logger.log(Level.INFO, "server socket channel class = " + channelClass);
        return channelClass;
    }

    static class HttpServerParentThreadFactory implements ThreadFactory {

        private long number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-parent-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }

    static class HttpServerChildThreadFactory implements ThreadFactory {

        private long number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-child-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }

    static class BlockingThreadFactory implements ThreadFactory {

        private long number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-pool-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }

    public static class BlockingThreadPoolExecutor extends ThreadPoolExecutor {

        private final Logger logger = Logger.getLogger(BlockingThreadPoolExecutor.class.getName());

        BlockingThreadPoolExecutor(int nThreads, int maxQueue, ThreadFactory threadFactory) {
            super(nThreads, nThreads,
                    0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(maxQueue), threadFactory);
        }

        /*
         * Examine Throwable or Error of a thread after execution just to log them.
         */
        @Override
        protected void afterExecute(Runnable runnable, Throwable terminationCause) {
            super.afterExecute(runnable, terminationCause);
            Throwable throwable = terminationCause;
            if (throwable == null && runnable instanceof Future<?>) {
                try {
                    Future<?> future = (Future<?>) runnable;
                    if (future.isDone()) {
                        future.get();
                    }
                } catch (CancellationException ce) {
                    logger.log(Level.FINEST, ce.getMessage(), ce);
                    throwable = ce;
                } catch (ExecutionException ee) {
                    logger.log(Level.FINEST, ee.getMessage(), ee);
                    throwable = ee.getCause();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.log(Level.FINEST, ie.getMessage(), ie);
                }
            }
            if (throwable != null) {
                logger.log(Level.SEVERE, throwable.getMessage(), throwable);
            }
        }
    }

    /**
     * HTTP server builder.
     */
    public static class Builder {

        private ByteBufAllocator byteBufAllocator;

        private EventLoopGroup parentEventLoopGroup;

        private EventLoopGroup childEventLoopGroup;

        private Class<? extends ServerSocketChannel> socketChannelClass;

        private final DefaultServerConfig serverConfig;

        private Builder(HttpServerDomain httpServerDomain) {
            this.serverConfig = new DefaultServerConfig();
            this.serverConfig.setAddress(httpServerDomain.getHttpAddress());
            addDomain(httpServerDomain);
        }

        public Builder enableDebug() {
            this.serverConfig.setDebug(true);
            return this;
        }

        public Builder setDebug(boolean debug) {
            this.serverConfig.setDebug(debug);
            return this;
        }

        public Builder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
            this.byteBufAllocator = byteBufAllocator;
            return this;
        }

        public Builder setTransportProviderName(String transportProviderName) {
            this.serverConfig.setTransportProviderName(transportProviderName);
            return this;
        }

        public Builder setParentEventLoopGroup(EventLoopGroup parentEventLoopGroup) {
            this.parentEventLoopGroup = parentEventLoopGroup;
            return this;
        }

        public Builder setChildEventLoopGroup(EventLoopGroup childEventLoopGroup) {
            this.childEventLoopGroup = childEventLoopGroup;
            return this;
        }

        public Builder setChannelClass(Class<? extends ServerSocketChannel> socketChannelClass) {
            this.socketChannelClass = socketChannelClass;
            return this;
        }

        public Builder setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.serverConfig.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        public Builder setReadTimeoutMillis(int readTimeoutMillis) {
            this.serverConfig.setReadTimeoutMillis(readTimeoutMillis);
            return this;
        }

        public Builder setIdleTimeoutMillis(int idleTimeoutMillis) {
            this.serverConfig.setIdleTimeoutMillis(idleTimeoutMillis);
            return this;
        }

        public Builder setParentThreadCount(int parentThreadCount) {
            this.serverConfig.setParentThreadCount(parentThreadCount);
            return this;
        }

        public Builder setChildThreadCount(int childThreadCount) {
            this.serverConfig.setChildThreadCount(childThreadCount);
            return this;
        }

        public Builder setBlockingThreadCount(int blockingThreadCount) {
            this.serverConfig.setBlockingThreadCount(blockingThreadCount);
            return this;
        }

        public Builder setBlockingQueueCount(int blockingQueueCount) {
            this.serverConfig.setBlockingQueueCount(blockingQueueCount);
            return this;
        }

        public Builder setTcpSendBufferSize(int tcpSendBufferSize) {
            this.serverConfig.setTcpSendBufferSize(tcpSendBufferSize);
            return this;
        }

        public Builder setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
            this.serverConfig.setTcpReceiveBufferSize(tcpReceiveBufferSize);
            return this;
        }

        public Builder setTcpNoDelay(boolean tcpNoDelay) {
            this.serverConfig.setTcpNodelay(tcpNoDelay);
            return this;
        }

        public Builder setReuseAddr(boolean reuseAddr) {
            this.serverConfig.setReuseAddr(reuseAddr);
            return this;
        }

        public Builder setBacklogSize(int backlogSize) {
            this.serverConfig.setBackLogSize(backlogSize);
            return this;
        }

        public Builder setMaxChunkSize(int maxChunkSize) {
            this.serverConfig.setMaxChunkSize(maxChunkSize);
            return this;
        }

        public Builder setMaxInitialLineLength(int maxInitialLineLength) {
            this.serverConfig.setMaxInitialLineLength(maxInitialLineLength);
            return this;
        }

        public Builder setMaxHeadersSize(int maxHeadersSize) {
            this.serverConfig.setMaxHeadersSize(maxHeadersSize);
            return this;
        }

        public Builder setMaxContentLength(int maxContentLength) {
            this.serverConfig.setMaxContentLength(maxContentLength);
            return this;
        }

        public Builder setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
            this.serverConfig.setMaxCompositeBufferComponents(maxCompositeBufferComponents);
            return this;
        }

        public Builder setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            this.serverConfig.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        public Builder enableCompression(boolean enableCompression) {
            this.serverConfig.setCompression(enableCompression);
            return this;
        }

        public Builder enableDecompression(boolean enableDecompression) {
            this.serverConfig.setDecompression(enableDecompression);
            return this;
        }

        public Builder enablePipelining(boolean enablePipelining) {
            this.serverConfig.setPipelining(enablePipelining);
            return this;
        }

        public Builder setPipeliningCapacity(int pipeliningCapacity) {
            this.serverConfig.setPipeliningCapacity(pipeliningCapacity);
            return this;
        }

        public Builder setInstallHttp2Upgrade(boolean installHttp2Upgrade) {
            this.serverConfig.setInstallHttp2Upgrade(installHttp2Upgrade);
            return this;
        }

        public Builder setOpenSSLSslProvider() {
            this.serverConfig.setOpenSSLSslProvider();
            return this;
        }

        public Builder setJdkSslProvider() {
            this.serverConfig.setJdkSslProvider();
            return this;
        }

        public Builder setTransportLayerSecurityProtocols(String... protocols) {
            this.serverConfig.setProtocols(protocols);
            return this;
        }

        public Builder addDomain(Domain<HttpEndpointResolver> domain) {
            this.serverConfig.addDomain(domain);
            return this;
        }

        public Builder setWebSocketFrameHandler(SimpleChannelInboundHandler<WebSocketFrame> webSocketFrameHandler) {
            this.serverConfig.setWebSocketFrameHandler(webSocketFrameHandler);
            return this;
        }

        public Server build() {
            int maxThreads = serverConfig.getBlockingThreadCount();
            int maxQueue = serverConfig.getBlockingQueueCount();
            BlockingThreadPoolExecutor executor = null;
            if (maxThreads > 0 && maxQueue > 0) {
                executor = new BlockingThreadPoolExecutor(maxThreads, maxQueue, new BlockingThreadFactory());
                executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) ->
                        logger.log(Level.SEVERE, "rejected: " + runnable));
            }
            if (serverConfig.isAutoDomain()) {
                // unpack subject alternative names into separate domains
                for (Domain<? extends EndpointResolver<?>> domain : serverConfig.getDomains()) {
                    try {
                        CertificateUtils.processSubjectAlternativeNames(domain.getCertificateChain(),
                                new CertificateUtils.SubjectAlternativeNamesProcessor() {
                                    @Override
                                    public void setServerName(String serverName) {
                                    }

                                    @Override
                                    public void setSubjectAlternativeName(String subjectAlternativeName) {
                                        HttpServerDomain alternativeDomain = HttpServerDomain.builder(domain)
                                                .setServerName(subjectAlternativeName)
                                                .build();
                                        addDomain(alternativeDomain);
                                    }
                                });
                    } catch (CertificateParsingException e) {
                        logger.log(Level.SEVERE, "domain " + domain + ": unable to parse certificate: " + e.getMessage(), e);
                    }
                }
            }
            for (Domain<? extends EndpointResolver<?>> domain : serverConfig.getDomains()) {
                if (domain.getCertificateChain() != null) {
                    for (X509Certificate certificate : domain.getCertificateChain()) {
                        try {
                            certificate.checkValidity();
                            logger.log(Level.INFO, "certificate " + certificate.getSubjectDN().getName() + " for " + domain + " is valid");
                        } catch (CertificateNotYetValidException | CertificateExpiredException e) {
                            logger.log(Level.SEVERE, "certificate " + certificate.getSubjectDN().getName() + " for " + domain + " is not valid: " + e.getMessage(), e);
                            if (!serverConfig.isAcceptInvalidCertificates()) {
                                throw new IllegalArgumentException(e);
                            }
                        }
                    }
                }
            }
            logger.log(Level.INFO, "configured domains: " + serverConfig.getDomains());
            return new Server(serverConfig, byteBufAllocator, parentEventLoopGroup, childEventLoopGroup,
                    socketChannelClass, executor);
        }
    }
}
