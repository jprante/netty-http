package org.xbib.netty.http.client;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;

import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;

public class ClientBuilder {

    private ByteBufAllocator byteBufAllocator;

    private EventLoopGroup eventLoopGroup;

    private Class<? extends SocketChannel> socketChannelClass;

    private ClientConfig clientConfig;

    public ClientBuilder() {
        this.clientConfig = new ClientConfig();
    }

    /**
     * Set byte buf allocator for payload in HTTP requests.
     * @param byteBufAllocator the byte buf allocator
     * @return this builder
     */
    public ClientBuilder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
        return this;
    }

    public ClientBuilder setEventLoop(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        return this;
    }

    public ClientBuilder setChannelClass(Class<SocketChannel> socketChannelClass) {
        this.socketChannelClass = socketChannelClass;
        return this;
    }

    public ClientBuilder setThreadCount(int threadCount) {
        clientConfig.setThreadCount(threadCount);
        return this;
    }

    public ClientBuilder setConnectTimeoutMillis(int connectTimeoutMillis) {
        clientConfig.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    public ClientBuilder setTcpSendBufferSize(int tcpSendBufferSize) {
        clientConfig.setTcpSendBufferSize(tcpSendBufferSize);
        return this;
    }

    public ClientBuilder setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
        clientConfig.setTcpReceiveBufferSize(tcpReceiveBufferSize);
        return this;
    }

    public ClientBuilder setTcpNodelay(boolean tcpNodelay) {
        clientConfig.setTcpNodelay(tcpNodelay);
        return this;
    }

    public ClientBuilder setKeepAlive(boolean keepAlive) {
        clientConfig.setKeepAlive(keepAlive);
        return this;
    }

    public ClientBuilder setReuseAddr(boolean reuseAddr) {
        clientConfig.setReuseAddr(reuseAddr);
        return this;
    }

    public ClientBuilder setMaxChunkSize(int maxChunkSize) {
        clientConfig.setMaxChunkSize(maxChunkSize);
        return this;
    }

    public ClientBuilder setMaxInitialLineLength(int maxInitialLineLength) {
        clientConfig.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    public ClientBuilder setMaxHeadersSize(int maxHeadersSize) {
        clientConfig.setMaxHeadersSize(maxHeadersSize);
        return this;
    }

    public ClientBuilder setMaxContentLength(int maxContentLength) {
        clientConfig.setMaxContentLength(maxContentLength);
        return this;
    }

    public ClientBuilder setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
        clientConfig.setMaxCompositeBufferComponents(maxCompositeBufferComponents);
        return this;
    }

    public ClientBuilder setMaxConnections(int maxConnections) {
        clientConfig.setMaxConnections(maxConnections);
        return this;
    }

    public ClientBuilder setReadTimeoutMillis(int readTimeoutMillis) {
        clientConfig.setReadTimeoutMillis(readTimeoutMillis);
        return this;
    }

    public ClientBuilder setEnableGzip(boolean enableGzip) {
        clientConfig.setEnableGzip(enableGzip);
        return this;
    }

    public ClientBuilder setSslProvider(SslProvider sslProvider) {
        clientConfig.setSslProvider(sslProvider);
        return this;
    }

    public ClientBuilder setJdkSslProvider() {
        clientConfig.setJdkSslProvider();
        return this;
    }

    public ClientBuilder setOpenSSLSslProvider() {
        clientConfig.setOpenSSLSslProvider();
        return this;
    }

    public ClientBuilder setCiphers(Iterable<String> ciphers) {
        clientConfig.setCiphers(ciphers);
        return this;
    }

    public ClientBuilder setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
        clientConfig.setCipherSuiteFilter(cipherSuiteFilter);
        return this;
    }

    public ClientBuilder setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        clientConfig.setTrustManagerFactory(trustManagerFactory);
        return this;
    }

    public ClientBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        clientConfig.setKeyCert(keyCertChainInputStream, keyInputStream);
        return this;
    }

    public ClientBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                         String keyPassword) {
        clientConfig.setKeyCert(keyCertChainInputStream, keyInputStream, keyPassword);
        return this;
    }

    public ClientBuilder setServerNameIdentification(boolean serverNameIdentification) {
        clientConfig.setServerNameIdentification(serverNameIdentification);
        return this;
    }

    public ClientBuilder setClientAuthMode(ClientAuthMode clientAuthMode) {
        clientConfig.setClientAuthMode(clientAuthMode);
        return this;
    }

    public ClientBuilder setHttpProxyHandler(HttpProxyHandler httpProxyHandler) {
        clientConfig.setHttpProxyHandler(httpProxyHandler);
        return this;
    }

    public Client build() {
        return new Client(clientConfig, byteBufAllocator, eventLoopGroup, socketChannelClass);
    }
}
