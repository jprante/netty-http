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
import org.xbib.netty.http.server.endpoint.NamedServer;
import org.xbib.netty.http.server.handler.http.HttpChannelInitializer;
import org.xbib.netty.http.server.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.common.SecurityUtil;
import org.xbib.netty.http.server.transport.HttpTransport;
import org.xbib.netty.http.server.transport.Http2Transport;
import org.xbib.netty.http.server.transport.Transport;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP server.
 */
public final class Server {

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

    private final ServerConfig serverConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup parentEventLoopGroup;

    private final EventLoopGroup childEventLoopGroup;

    private final Class<? extends ServerSocketChannel> socketChannelClass;

    private final ServerBootstrap bootstrap;

    private ChannelFuture channelFuture;

    /**
     * Create a new HTTP server. Use {@link #builder(HttpAddress)} to build HTTP client instance.
     * @param serverConfig server configuration
     * @param byteBufAllocator byte buf allocator
     * @param parentEventLoopGroup parent event loop group
     * @param childEventLoopGroup child event loop group
     * @param socketChannelClass socket channel class
     */
    private Server(ServerConfig serverConfig,
                  ByteBufAllocator byteBufAllocator,
                  EventLoopGroup parentEventLoopGroup,
                  EventLoopGroup childEventLoopGroup,
                  Class<? extends ServerSocketChannel> socketChannelClass) {
        Objects.requireNonNull(serverConfig);
        this.serverConfig = serverConfig;
        this.byteBufAllocator = byteBufAllocator != null ?
                byteBufAllocator : ByteBufAllocator.DEFAULT;
        this.parentEventLoopGroup = createParentEventLoopGroup(serverConfig, parentEventLoopGroup);
        this.childEventLoopGroup = createChildEventLoopGroup(serverConfig, childEventLoopGroup);
        this.socketChannelClass = createSocketChannelClass(serverConfig, socketChannelClass);
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
        if (serverConfig.isDebug()) {
            bootstrap.handler(new LoggingHandler("bootstrap-server", serverConfig.getDebugLogLevel()));
        }
        DomainNameMapping<SslContext> domainNameMapping = createDomainNameMapping();
        if (serverConfig.getAddress().getVersion().majorVersion() == 1) {
            HttpChannelInitializer httpChannelInitializer = new HttpChannelInitializer(this,
                    serverConfig.getAddress(), domainNameMapping);
            bootstrap.childHandler(httpChannelInitializer);
        } else {
            Http2ChannelInitializer http2ChannelInitializer = new Http2ChannelInitializer(this,
                    serverConfig.getAddress(), domainNameMapping);
            bootstrap.childHandler(http2ChannelInitializer);
        }
    }

    public static Builder builder() {
        return new Builder(HttpAddress.http1("localhost", 8008));
    }

    public static Builder builder(HttpAddress httpAddress) {
        return new Builder(httpAddress);
    }

    public static Builder builder(NamedServer namedServer) {
        return new Builder(namedServer.getHttpAddress(), namedServer);
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /**
     * Returns the named server with the given name.
     *
     * @param name the name of the virtual host to return, or null for
     *             the default virtual host
     * @return the virtual host with the given name, or null if it doesn't exist
     */
    public NamedServer getNamedServer(String name) {
        return serverConfig.getNamedServers().get(name);
    }

    public NamedServer getDefaultNamedServer() {
        return serverConfig.getDefaultNamedServer();
    }

    /**
     * Start accepting incoming connections.
     * @return the channel future
     * @throws IOException if channel future sync is interrupted
     */
    public ChannelFuture accept() throws IOException {
        logger.log(Level.INFO, () -> "trying to bind to " + serverConfig.getAddress());
        try {
            this.channelFuture = bootstrap.bind(serverConfig.getAddress().getInetSocketAddress()).await().sync();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        logger.log(Level.INFO, () -> ServerName.getServerName() + " ready, listening on " + serverConfig.getAddress());
        return channelFuture;
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "JDK ciphers: " + SecurityUtil.Defaults.JDK_CIPHERS);
        logger.log(level, () -> "OpenSSL ciphers: " + SecurityUtil.Defaults.OPENSSL_CIPHERS);
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable());
        logger.log(level, () -> "OpenSSL ALPN support: " + OpenSsl.isAlpnSupported());
        logger.log(level, () -> "Installed ciphers on default server: " +
                (serverConfig.getAddress().isSecure() ? getDefaultNamedServer().getSslContext().cipherSuites() : ""));
        logger.log(level, () -> "Local host name: " + NetworkUtils.getLocalHostName("localhost"));
        logger.log(level, () -> "Parent event loop group: " + parentEventLoopGroup + " threads=" + serverConfig.getParentThreadCount());
        logger.log(level, () -> "Child event loop group: " + childEventLoopGroup + " threads=" +serverConfig.getChildThreadCount());
        logger.log(level, () -> "Socket: " + socketChannelClass.getName());
        logger.log(level, () -> "Allocator: " + byteBufAllocator.getClass().getName());
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public Transport newTransport(HttpVersion httpVersion) {
        return httpVersion.majorVersion() == 1 ? new HttpTransport(this) : new Http2Transport(this);
    }

    public synchronized void shutdownGracefully() throws IOException {
        logger.log(Level.FINE, "shutting down gracefully");
        // first, shut down threads, then server socket
        childEventLoopGroup.shutdownGracefully();
        parentEventLoopGroup.shutdownGracefully();
        try {
            if (channelFuture != null) {
                channelFuture.channel().closeFuture().sync();
            }
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
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

    private DomainNameMapping<SslContext> createDomainNameMapping() {
        if (serverConfig.getDefaultNamedServer() == null) {
            throw new IllegalStateException("no default named server (with name '*') configured, unable to continue");
        }
        DomainNameMapping<SslContext> domainNameMapping = null;
        if (serverConfig.getAddress().isSecure() && serverConfig.getDefaultNamedServer().getSslContext() != null) {
            DomainNameMappingBuilder<SslContext> mappingBuilder =
                    new DomainNameMappingBuilder<>(serverConfig.getDefaultNamedServer().getSslContext());
            for (NamedServer namedServer : serverConfig.getNamedServers().values()) {
                String name = namedServer.getName();
                if (!"*".equals(name)) {
                    mappingBuilder.add(name, namedServer.getSslContext());
                }
            }
            domainNameMapping = mappingBuilder.build();
        }
        return domainNameMapping;
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

    /**
     * HTTP server builder.
     */
    public static class Builder {

        private ByteBufAllocator byteBufAllocator;

        private EventLoopGroup parentEventLoopGroup;

        private EventLoopGroup childEventLoopGroup;

        private Class<? extends ServerSocketChannel> socketChannelClass;

        private ServerConfig serverConfig;

        Builder(HttpAddress httpAddress) {
            this(httpAddress, NamedServer.builder(httpAddress, "*").build());
        }

        Builder(HttpAddress httpAddress, NamedServer defaultNamedServer) {
            this.serverConfig = new ServerConfig();
            this.serverConfig.setAddress(httpAddress);
            this.serverConfig.add(defaultNamedServer);
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

        public Builder setParentThreadCount(int parentThreadCount) {
            this.serverConfig.setParentThreadCount(parentThreadCount);
            return this;
        }

        public Builder setChildThreadCount(int childThreadCount) {
            this.serverConfig.setChildThreadCount(childThreadCount);
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

        public Builder setReadTimeoutMillis(int readTimeoutMillis) {
            this.serverConfig.setReadTimeoutMillis(readTimeoutMillis);
            return this;
        }

        public Builder setConnectionTimeoutMillis(int connectionTimeoutMillis) {
            this.serverConfig.setConnectTimeoutMillis(connectionTimeoutMillis);
            return this;
        }

        public Builder setIdleTimeoutMillis(int idleTimeoutMillis) {
            this.serverConfig.setIdleTimeoutMillis(idleTimeoutMillis);
            return this;
        }

        public Builder setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
            this.serverConfig.setWriteBufferWaterMark(writeBufferWaterMark);
            return this;
        }

        public Builder setEnablCcompression(boolean enablCcompression) {
            this.serverConfig.setCompression(enablCcompression);
            return this;
        }

        public Builder setEnableDecompression(boolean enableDecompression) {
            this.serverConfig.setDecompression(enableDecompression);
            return this;
        }

        public Builder setInstallHttp2Upgrade(boolean installHttp2Upgrade) {
            this.serverConfig.setInstallHttp2Upgrade(installHttp2Upgrade);
            return this;
        }

        public Builder addServer(NamedServer namedServer) {
            this.serverConfig.add(namedServer);
            return this;
        }

        public Server build() {
            return new Server(serverConfig, byteBufAllocator, parentEventLoopGroup, childEventLoopGroup, socketChannelClass);
        }
    }

}
