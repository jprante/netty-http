package org.xbib.netty.http.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.DomainNameMapping;
import io.netty.util.DomainNameMappingBuilder;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.context.VirtualServer;
import org.xbib.netty.http.server.handler.http1.HttpChannelInitializer;
import org.xbib.netty.http.server.handler.http2.Http2ChannelInitializer;
import org.xbib.netty.http.server.transport.Http1ServerTransport;
import org.xbib.netty.http.server.transport.Http2ServerTransport;
import org.xbib.netty.http.server.transport.ServerTransport;
import org.xbib.netty.http.server.util.NetworkUtils;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.HashMap;
import java.util.Map;
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
        //NetworkUtils.extendSystemProperties();
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
        //if (System.getProperty("io.netty.leakDetection.level") == null) {
        //    System.setProperty("io.netty.leakDetection.level", "paranoid");
        //}
    }

    private final ServerConfig serverConfig;

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup parentEventLoopGroup;

    private final EventLoopGroup childEventLoopGroup;

    private final Class<? extends ServerSocketChannel> socketChannelClass;

    private final ServerBootstrap bootstrap;

    private final Map<String, VirtualServer> virtualServerMap;

    private ChannelFuture channelFuture;

    /**
     * Create a new HTTP server. Use {@link #builder()} to build HTTP client instance.
     */
    public Server(ServerConfig serverConfig,
                  ByteBufAllocator byteBufAllocator,
                  EventLoopGroup parentEventLoopGroup,
                  EventLoopGroup childEventLoopGroup,
                  Class<? extends ServerSocketChannel> socketChannelClass) throws SSLException {
        Objects.requireNonNull(serverConfig);
        this.serverConfig = serverConfig;
        initializeTrustManagerFactory(serverConfig);
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
        this.virtualServerMap = new HashMap<>();
        for (VirtualServer virtualServer : serverConfig.getVirtualServers()) {
            String name = virtualServer.getName();
            virtualServerMap.put(name, virtualServer);
            for (String alias : virtualServer.getAliases()) {
                virtualServerMap.put(alias, virtualServer);
            }
        }
        DomainNameMapping<SslContext> domainNameMapping = null;
        if (serverConfig.getAddress().isSecure()) {
            SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(serverConfig.getKeyCertChainInputStream(),
                    serverConfig.getKeyInputStream(), serverConfig.getKeyPassword())
                    .sslProvider(serverConfig.getSslProvider())
                    .ciphers(serverConfig.getCiphers(), serverConfig.getCipherSuiteFilter());
            if (serverConfig.getAddress().getVersion().majorVersion() == 2) {
                sslContextBuilder.applicationProtocolConfig(newApplicationProtocolConfig());
            }
            SslContext sslContext = sslContextBuilder.build();
            DomainNameMappingBuilder<SslContext> mappingBuilder = new DomainNameMappingBuilder<>(sslContext);
            for (VirtualServer virtualServer : serverConfig.getVirtualServers()) {
                String name = virtualServer.getName();
                mappingBuilder.add( name == null ? "*" : name, sslContext);
            }
            domainNameMapping = mappingBuilder.build();
        }
        HttpAddress httpAddress = serverConfig.getAddress();
        if (httpAddress.getVersion().majorVersion() == 1) {
            HttpChannelInitializer httpChannelInitializer = new HttpChannelInitializer(this,
                    httpAddress, domainNameMapping);
            bootstrap.childHandler(httpChannelInitializer);
        } else {
            Http2ChannelInitializer initializer = new Http2ChannelInitializer(this,
                    httpAddress, domainNameMapping);
            bootstrap.childHandler(initializer);
        }
    }

    public static ServerBuilder builder() {
        return new ServerBuilder();
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    /**
     * Returns the virtual host with the given name.
     *
     * @param name the name of the virtual host to return, or null for
     *             the default virtual host
     * @return the virtual host with the given name, or null if it doesn't exist
     */
    public VirtualServer getVirtualServer(String name) {
        return virtualServerMap.get(name);
    }

    public VirtualServer getDefaultVirtualServer() {
        return virtualServerMap.get(null);
    }

    /**
     * Start accepting incoming connections.
     */
    public ChannelFuture accept() {
        logger.log(Level.INFO, () -> "trying to bind to " + serverConfig.getAddress());
        this.channelFuture = bootstrap.bind(serverConfig.getAddress().getInetSocketAddress());
        logger.log(Level.INFO, () -> ServerName.getServerName() + " ready, listening on " + serverConfig.getAddress());
        return channelFuture;
    }

    public void logDiagnostics(Level level) {
        logger.log(level, () -> "OpenSSL available: " + OpenSsl.isAvailable() +
                " OpenSSL ALPN support: " + OpenSsl.isAlpnSupported() +
                " Local host name: " + NetworkUtils.getLocalHostName("localhost") +
                " parent event loop group: " + parentEventLoopGroup +
                " child event loop group: " + childEventLoopGroup +
                " socket: " + socketChannelClass.getName() +
                " allocator: " + byteBufAllocator.getClass().getName());
        logger.log(level, NetworkUtils::displayNetworkInterfaces);
    }

    public ServerTransport newTransport(HttpVersion httpVersion) {
        return httpVersion.majorVersion() == 1 ? new Http1ServerTransport(this) : new Http2ServerTransport(this);
    }

    public synchronized void shutdownGracefully() throws IOException {
        // first, shut down threads, then server socket
        childEventLoopGroup.shutdownGracefully();
        parentEventLoopGroup.shutdownGracefully();
        try {
            channelFuture.channel().closeFuture().sync();
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

    /**
     * Initialize trust manager factory once per server lifecycle.
     * @param serverConfig the server config
     */
    private static void initializeTrustManagerFactory(ServerConfig serverConfig) {
        TrustManagerFactory trustManagerFactory = serverConfig.getTrustManagerFactory();
        if (trustManagerFactory != null) {
            try {
                trustManagerFactory.init(serverConfig.getTrustManagerKeyStore());
            } catch (KeyStoreException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

    private static ApplicationProtocolConfig newApplicationProtocolConfig() {
        return new ApplicationProtocolConfig(ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1);
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
}
