package org.xbib.netty.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.NetworkUtils;
import org.xbib.netty.http.server.api.HttpChannelInitializer;
import org.xbib.netty.http.server.api.ProtocolProvider;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.common.security.SecurityUtil;
import org.xbib.netty.http.server.transport.HttpServerRequest;
import org.xbib.netty.http.server.api.Transport;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
        // extend Java system properties by detected network interfaces
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

    private static final AtomicLong requestCounter = new AtomicLong();

    private static final AtomicLong responseCounter = new AtomicLong();

    private final ServerConfig serverConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup parentEventLoopGroup;

    private final EventLoopGroup childEventLoopGroup;

    /**
     * A thread pool for executing blocking tasks.
     */
    private final BlockingThreadPoolExecutor executor;

    private final Class<? extends ServerSocketChannel> socketChannelClass;

    private final ServerBootstrap bootstrap;

    private ChannelFuture channelFuture;

    private final List<ProtocolProvider<HttpChannelInitializer, Transport>> protocolProviders;

    /**
     * Create a new HTTP server.
     * Use {@link #builder(HttpAddress)} to build HTTP instance.
     *
     * @param serverConfig server configuration
     * @param byteBufAllocator byte buf allocator
     * @param parentEventLoopGroup parent event loop group
     * @param childEventLoopGroup child event loop group
     * @param socketChannelClass socket channel class
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
        this.byteBufAllocator = byteBufAllocator != null ? byteBufAllocator : ByteBufAllocator.DEFAULT;
        this.parentEventLoopGroup = createParentEventLoopGroup(serverConfig, parentEventLoopGroup);
        this.childEventLoopGroup = createChildEventLoopGroup(serverConfig, childEventLoopGroup);
        this.socketChannelClass = createSocketChannelClass(serverConfig, socketChannelClass);
        this.executor = executor;
        this.protocolProviders =new ArrayList<>();
        for (ProtocolProvider<HttpChannelInitializer, Transport> provider : ServiceLoader.load(ProtocolProvider.class)) {
            protocolProviders.add(provider);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "protocol provider up: " + provider.transportClass());
            }
        }
        this.bootstrap = new ServerBootstrap()
                .group(this.parentEventLoopGroup, this.childEventLoopGroup)
                .channel(this.socketChannelClass)
                .option(ChannelOption.ALLOCATOR, this.byteBufAllocator)
                .option(ChannelOption.SO_REUSEADDR, serverConfig.isReuseAddr())
                .option(ChannelOption.SO_RCVBUF, serverConfig.getTcpReceiveBufferSize())
                .option(ChannelOption.SO_BACKLOG, serverConfig.getBackLogSize())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverConfig.getConnectTimeoutMillis())
                .childOption(ChannelOption.ALLOCATOR, this.byteBufAllocator)
                .childOption(ChannelOption.SO_REUSEADDR, serverConfig.isReuseAddr())
                .childOption(ChannelOption.TCP_NODELAY, serverConfig.isTcpNodelay())
                .childOption(ChannelOption.SO_SNDBUF, serverConfig.getTcpSendBufferSize())
                .childOption(ChannelOption.SO_RCVBUF, serverConfig.getTcpReceiveBufferSize())
                .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, serverConfig.getConnectTimeoutMillis())
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, serverConfig.getWriteBufferWaterMark());
        if (serverConfig.isTrafficDebug()) {
            bootstrap.handler(new LoggingHandler("bootstrap-server", serverConfig.getTrafficDebugLogLevel()));
        }
        if (serverConfig.getDefaultDomain() == null) {
            throw new IllegalStateException("no default named server (with name '*') configured, unable to continue");
        }
        DomainNameMapping<SslContext> domainNameMapping = null;
        if (serverConfig.getAddress().isSecure() && serverConfig.getDefaultDomain().getSslContext() != null) {
            DomainNameMappingBuilder<SslContext> mappingBuilder =
                    new DomainNameMappingBuilder<>(serverConfig.getDefaultDomain().getSslContext());
            for (Domain domain : serverConfig.getDomains()) {
                String name = domain.getName();
                if (!"*".equals(name)) {
                    mappingBuilder.add(name, domain.getSslContext());
                }
            }
            domainNameMapping = mappingBuilder.build();
        }
        bootstrap.childHandler(findChannelInitializer(serverConfig.getAddress().getVersion().majorVersion(),
                serverConfig.getAddress(), domainNameMapping));
    }

    public static Builder builder() {
        return builder(HttpAddress.http1("localhost", 8008));
    }

    public static Builder builder(HttpAddress httpAddress) {
        return new Builder(httpAddress);
    }

    public static Builder builder(Domain domain) {
        return new Builder(domain);
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /**
     * Start accepting incoming connections.
     * @return the channel future
     * @throws IOException if channel future sync is interrupted
     */
    public ChannelFuture accept() throws IOException {
        HttpAddress httpAddress = serverConfig.getAddress();
        logger.log(Level.INFO, () -> "trying to bind to " + httpAddress);
        try {
            this.channelFuture = bootstrap.bind(httpAddress.getInetSocketAddress()).await().sync();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        logger.log(Level.INFO, () -> ServerName.getServerName() + " ready, listening on " + httpAddress);
        return channelFuture;
    }

    /**
     * Returns the named server with the given name.
     *
     * @param name the name of the virtual host to return or null for the
     *             default domain
     * @return the virtual host with the given name or the default domain
     */
    public Domain getNamedServer(String name) {
        Domain domain = serverConfig.getDomain(name);
        if (domain == null) {
            domain = serverConfig.getDefaultDomain();
        }
        return domain;
    }

    public void handle(Domain domain, HttpServerRequest serverRequest, ServerResponse serverResponse)
            throws IOException {
        if (executor != null) {
            executor.submit(() -> {
                try {
                    domain.handle(serverRequest, serverResponse);
                } catch (IOException e) {
                    executor.afterExecute(null, e);
                } finally {
                    serverRequest.release();
                }
            });
        } else {
            try {
                domain.handle(serverRequest, serverResponse);
            } finally {
                serverRequest.release();
            }
        }
    }

    public BlockingThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "JDK ciphers: " + SecurityUtil.Defaults.JDK_CIPHERS);
        logger.log(level, () -> "OpenSSL ciphers: " + SecurityUtil.Defaults.OPENSSL_CIPHERS);
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable());
        logger.log(level, () -> "OpenSSL ALPN support: " + OpenSsl.isAlpnSupported());
        logger.log(level, () -> "Installed ciphers on default server: " +
                (serverConfig.getAddress().isSecure() ? serverConfig.getDefaultDomain().getSslContext().cipherSuites() : ""));
        logger.log(level, () -> "Local host name: " + NetworkUtils.getLocalHostName("localhost"));
        logger.log(level, () -> "Parent event loop group: " + parentEventLoopGroup + " threads=" + serverConfig.getParentThreadCount());
        logger.log(level, () -> "Child event loop group: " + childEventLoopGroup + " threads=" +serverConfig.getChildThreadCount());
        logger.log(level, () -> "Socket: " + socketChannelClass.getName());
        logger.log(level, () -> "Allocator: " + byteBufAllocator.getClass().getName());
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public AtomicLong getRequestCounter() {
        return requestCounter;
    }

    public AtomicLong getResponseCounter() {
        return responseCounter;
    }

    public Transport newTransport(HttpVersion httpVersion) {
        for (ProtocolProvider<HttpChannelInitializer, Transport> protocolProvider : protocolProviders) {
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

    @Override
    public void close() {
        try {
            shutdownGracefully();
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
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

    private HttpChannelInitializer findChannelInitializer(int majorVersion,
                                                          HttpAddress httpAddress,
                                                          DomainNameMapping<SslContext> domainNameMapping) {
        for (ProtocolProvider<HttpChannelInitializer, Transport> protocolProvider : protocolProviders) {
            if (protocolProvider.supportsMajorVersion(majorVersion)) {
                try {
                    return protocolProvider.initializerClass()
                            .getConstructor(Server.class, HttpAddress.class, DomainNameMapping.class)
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
            eventLoopGroup = serverConfig.isEpoll() ?
                    new EpollEventLoopGroup(serverConfig.getParentThreadCount(), new HttpServerParentThreadFactory()) :
                    new NioEventLoopGroup(serverConfig.getParentThreadCount(), new HttpServerParentThreadFactory());
        }
        return eventLoopGroup;
    }

    private static EventLoopGroup createChildEventLoopGroup(ServerConfig serverConfig,
                                                             EventLoopGroup childEventLoopGroup ) {
        EventLoopGroup eventLoopGroup = childEventLoopGroup;
        if (eventLoopGroup == null) {
            eventLoopGroup = serverConfig.isEpoll() ?
                    new EpollEventLoopGroup(serverConfig.getChildThreadCount(), new HttpServerChildThreadFactory()) :
                    new NioEventLoopGroup(serverConfig.getChildThreadCount(), new HttpServerChildThreadFactory());
        }
        return eventLoopGroup;
    }

    private static Class<? extends ServerSocketChannel> createSocketChannelClass(ServerConfig serverConfig,
                                                                                 Class<? extends ServerSocketChannel> socketChannelClass) {
        Class<? extends ServerSocketChannel> channelClass = socketChannelClass;
        if (channelClass == null) {
            if (serverConfig.isEpoll() && Epoll.isAvailable()) {
                channelClass = EpollServerSocketChannel.class;
            } else {
                channelClass = NioServerSocketChannel.class;
            }
        }
        return channelClass;
    }

    static class HttpServerParentThreadFactory implements ThreadFactory {

        private int number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-parent-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }

    static class HttpServerChildThreadFactory implements ThreadFactory {

        private int number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-child-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    }

    static class BlockingThreadFactory implements ThreadFactory {

        private int number = 0;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "org-xbib-netty-http-server-pool-" + (number++));
            thread.setDaemon(true);
            return thread;
        }
    };

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

        private ServerConfig serverConfig;

        private Builder(HttpAddress httpAddress) {
            this(Domain.builder(httpAddress, "*").build());
        }

        private Builder(Domain defaultDomain) {
            this.serverConfig = new ServerConfig();
            this.serverConfig.setAddress(defaultDomain.getHttpAddress());
            addDomain(defaultDomain);
        }

        public Builder enableDebug() {
            this.serverConfig.enableDebug();
            return this;
        }

        public Builder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
            this.byteBufAllocator = byteBufAllocator;
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

        public Builder setUseEpoll(boolean useEpoll) {
            this.serverConfig.setEpoll(useEpoll);
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

        public Builder setInstallHttp2Upgrade(boolean installHttp2Upgrade) {
            this.serverConfig.setInstallHttp2Upgrade(installHttp2Upgrade);
            return this;
        }

        public Builder setTransportLayerSecurityProtocols(String[] protocols) {
            this.serverConfig.setProtocols(protocols);
            return this;
        }

        public Builder addDomain(Domain domain) {
            this.serverConfig.putDomain(domain);
            logger.log(Level.FINE, "adding named server: " + domain);
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
            return new Server(serverConfig, byteBufAllocator,
                    parentEventLoopGroup, childEventLoopGroup, socketChannelClass,
                    executor);
        }
    }
}
