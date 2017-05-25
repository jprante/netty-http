/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.client.internal.HttpClientThreadFactory;
import org.xbib.netty.http.client.util.ClientAuthMode;

import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.InetSocketAddress;

/**
 *
 */
public class HttpClientBuilder implements HttpClientChannelContextDefaults {

    private ByteBufAllocator byteBufAllocator;

    private EventLoopGroup eventLoopGroup;

    private Class<? extends SocketChannel> socketChannelClass;

    private Bootstrap bootstrap;

    // let Netty decide about thread number, default is Runtime.getRuntime().availableProcessors() * 2
    private int threads = 0;

    private boolean tcpNodelay = DEFAULT_TCP_NODELAY;

    private boolean keepAlive = DEFAULT_SO_KEEPALIVE;

    private boolean reuseAddr = DEFAULT_SO_REUSEADDR;

    private int tcpSendBufferSize = DEFAULT_TCP_SEND_BUFFER_SIZE;

    private int tcpReceiveBufferSize = DEFAULT_TCP_RECEIVE_BUFFER_SIZE;

    private int maxChunkSize = DEFAULT_MAX_CHUNK_SIZE;

    private int maxInitialLineLength = DEFAULT_MAX_INITIAL_LINE_LENGTH;

    private int maxHeadersSize = DEFAULT_MAX_HEADERS_SIZE;

    private int maxConnections = DEFAULT_MAX_CONNECTIONS;

    private int maxContentLength = DEFAULT_MAX_CONTENT_LENGTH;

    private int maxCompositeBufferComponents = DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS;

    private int connectTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    private int readTimeoutMillis = DEFAULT_TIMEOUT_MILLIS;

    private boolean enableGzip = DEFAULT_ENABLE_GZIP;

    private boolean installHttp2Upgrade = DEFAULT_INSTALL_HTTP_UPGRADE2;

    private SslProvider sslProvider = DEFAULT_SSL_PROVIDER;

    private Iterable<String> ciphers = DEFAULT_CIPHERS;

    private CipherSuiteFilter cipherSuiteFilter = DEFAULT_CIPHER_SUITE_FILTER;

    private TrustManagerFactory trustManagerFactory = DEFAULT_TRUST_MANAGER_FACTORY;

    private InputStream keyCertChainInputStream;

    private InputStream keyInputStream;

    private String keyPassword;

    private boolean useServerNameIdentification = DEFAULT_USE_SERVER_NAME_IDENTIFICATION;

    private ClientAuthMode clientAuthMode = DEFAULT_SSL_CLIENT_AUTH_MODE;

    private HttpProxyHandler httpProxyHandler;

    private Socks4ProxyHandler socks4ProxyHandler;

    private Socks5ProxyHandler socks5ProxyHandler;

    /**
     * Set byte buf allocator for payload in HTTP requests.
     * @param byteBufAllocator the byte buf allocator
     * @return this builder
     */
    public HttpClientBuilder withByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
        return this;
    }

    public HttpClientBuilder withEventLoop(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public HttpClientBuilder withChannelClass(Class<SocketChannel> socketChannelClass) {
        this.socketChannelClass = socketChannelClass;
        return this;
    }

    public HttpClientBuilder withBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
        return this;
    }

    public HttpClientBuilder setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    public HttpClientBuilder setThreadCount(int count) {
        this.threads = count;
        return this;
    }

    public HttpClientBuilder setTcpSendBufferSize(int tcpSendBufferSize) {
        this.tcpSendBufferSize = tcpSendBufferSize;
        return this;
    }

    public HttpClientBuilder setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
        this.tcpReceiveBufferSize = tcpReceiveBufferSize;
        return this;
    }

    public HttpClientBuilder setTcpNodelay(boolean tcpNodelay) {
        this.tcpNodelay = tcpNodelay;
        return this;
    }

    public HttpClientBuilder setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public HttpClientBuilder setReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
        return this;
    }

    public HttpClientBuilder setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public HttpClientBuilder setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public HttpClientBuilder setMaxHeadersSize(int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
        return this;
    }

    public HttpClientBuilder setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }

    public HttpClientBuilder setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
        this.maxCompositeBufferComponents = maxCompositeBufferComponents;
        return this;
    }

    public HttpClientBuilder setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        return this;
    }

    public HttpClientBuilder setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
        return this;
    }

    public HttpClientBuilder setEnableGzip(boolean enableGzip) {
        this.enableGzip = enableGzip;
        return this;
    }

    public HttpClientBuilder setInstallHttp2Upgrade(boolean installHttp2Upgrade) {
        this.installHttp2Upgrade = installHttp2Upgrade;
        return this;
    }

    public HttpClientBuilder withSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
        return this;
    }

    public HttpClientBuilder withJdkSslProvider() {
        this.sslProvider = SslProvider.JDK;
        return this;
    }

    public HttpClientBuilder withOpenSSLSslProvider() {
        this.sslProvider = SslProvider.OPENSSL;
        return this;
    }

    public HttpClientBuilder withCiphers(Iterable<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    public HttpClientBuilder withCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
        this.cipherSuiteFilter = cipherSuiteFilter;
        return this;
    }

    public HttpClientBuilder withTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    public HttpClientBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        this.keyCertChainInputStream = keyCertChainInputStream;
        this.keyInputStream = keyInputStream;
        return this;
    }

    public HttpClientBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                        String keyPassword) {
        this.keyCertChainInputStream = keyCertChainInputStream;
        this.keyInputStream = keyInputStream;
        this.keyPassword = keyPassword;
        return this;
    }

    public HttpClientBuilder setUseServerNameIdentification(boolean useServerNameIdentification) {
        this.useServerNameIdentification = useServerNameIdentification;
        return this;
    }

    public HttpClientBuilder setClientAuthMode(ClientAuthMode clientAuthMode) {
        this.clientAuthMode = clientAuthMode;
        return this;
    }

    public HttpClientBuilder setHttpProxyHandler(InetSocketAddress proxyAddress) {
        this.httpProxyHandler = new HttpProxyHandler(proxyAddress);
        return this;
    }

    public HttpClientBuilder setHttpProxyHandler(InetSocketAddress proxyAddress, String username, String password) {
        this.httpProxyHandler = new HttpProxyHandler(proxyAddress, username, password);
        return this;
    }

    public HttpClientBuilder setSocks4Proxy(InetSocketAddress proxyAddress) {
        this.socks4ProxyHandler = new Socks4ProxyHandler(proxyAddress);
        return this;
    }

    public HttpClientBuilder setSocks4Proxy(InetSocketAddress proxyAddress, String username) {
        this.socks4ProxyHandler = new Socks4ProxyHandler(proxyAddress, username);
        return this;
    }

    public HttpClientBuilder setSocks5Proxy(InetSocketAddress proxyAddress) {
        this.socks5ProxyHandler = new Socks5ProxyHandler(proxyAddress);
        return this;
    }

    public HttpClientBuilder setSocks5Proxy(InetSocketAddress proxyAddress, String username, String password) {
        this.socks5ProxyHandler = new Socks5ProxyHandler(proxyAddress, username, password);
        return this;
    }

    /**
     * Build a HTTP client.
     * @return  a http client
     */
    public HttpClient build() {
        if (byteBufAllocator == null) {
            byteBufAllocator = PooledByteBufAllocator.DEFAULT;
        }
        if (eventLoopGroup == null) {
            eventLoopGroup = new NioEventLoopGroup(threads, new HttpClientThreadFactory());
        }
        if (socketChannelClass == null) {
            socketChannelClass = NioSocketChannel.class;
        }
        if (bootstrap == null) {
            bootstrap = new Bootstrap();
        }
        bootstrap.option(ChannelOption.TCP_NODELAY, tcpNodelay);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, keepAlive);
        bootstrap.option(ChannelOption.SO_REUSEADDR, reuseAddr);
        bootstrap.option(ChannelOption.SO_SNDBUF, tcpSendBufferSize);
        bootstrap.option(ChannelOption.SO_RCVBUF, tcpReceiveBufferSize);
        bootstrap.option(ChannelOption.ALLOCATOR, byteBufAllocator);
        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
        bootstrap.group(eventLoopGroup);
        bootstrap.channel(socketChannelClass);
        final HttpClientChannelContext httpClientChannelContext =
                new HttpClientChannelContext(maxInitialLineLength, maxHeadersSize, maxChunkSize, maxContentLength,
                        maxCompositeBufferComponents,
                        readTimeoutMillis, enableGzip, installHttp2Upgrade,
                        sslProvider, ciphers, cipherSuiteFilter, trustManagerFactory,
                        keyCertChainInputStream, keyInputStream, keyPassword,
                        useServerNameIdentification, clientAuthMode,
                        httpProxyHandler, socks4ProxyHandler, socks5ProxyHandler);
        return new HttpClient(byteBufAllocator, eventLoopGroup, bootstrap, maxConnections, httpClientChannelContext);
    }
}
