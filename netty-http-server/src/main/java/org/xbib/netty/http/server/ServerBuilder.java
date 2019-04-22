package org.xbib.netty.http.server;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.context.VirtualServer;
import org.xbib.netty.http.server.security.tls.SelfSignedCertificate;

import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;

/**
 * HTTP server builder.
 */
public class ServerBuilder {

    private ByteBufAllocator byteBufAllocator;

    private EventLoopGroup parentEventLoopGroup;

    private EventLoopGroup childEventLoopGroup;

    private Class<? extends ServerSocketChannel> socketChannelClass;

    private ServerConfig serverConfig;

    public ServerBuilder() {
        this.serverConfig = new ServerConfig();
    }

    public ServerBuilder enableDebug() {
        this.serverConfig.enableDebug();
        return this;
    }

    public ServerBuilder bind(HttpAddress httpAddress) {
        this.serverConfig.setAddress(httpAddress);
        return this;
    }

    public ServerBuilder host(String bindhost, int bindPort) {
        this.serverConfig.setAddress(HttpAddress.http2(bindhost, bindPort));
        return this;
    }

    public ServerBuilder port(int bindPort) {
        this.serverConfig.setAddress(HttpAddress.http2(null, bindPort));
        return this;
    }

    public ServerBuilder setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
        return this;
    }

    public ServerBuilder setParentEventLoopGroup(EventLoopGroup parentEventLoopGroup) {
        this.parentEventLoopGroup = parentEventLoopGroup;
        return this;
    }

    public ServerBuilder setChildEventLoopGroup(EventLoopGroup childEventLoopGroup) {
        this.childEventLoopGroup = childEventLoopGroup;
        return this;
    }

    public ServerBuilder setChannelClass(Class<? extends ServerSocketChannel> socketChannelClass) {
        this.socketChannelClass = socketChannelClass;
        return this;
    }

    public ServerBuilder setUseEpoll(boolean useEpoll) {
        this.serverConfig.setEpoll(useEpoll);
        return this;
    }

    public ServerBuilder setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.serverConfig.setConnectTimeoutMillis(connectTimeoutMillis);
        return this;
    }

    public ServerBuilder setParentThreadCount(int parentThreadCount) {
        this.serverConfig.setParentThreadCount(parentThreadCount);
        return this;
    }

    public ServerBuilder setChildThreadCount(int childThreadCount) {
        this.serverConfig.setChildThreadCount(childThreadCount);
        return this;
    }

    public ServerBuilder setTcpSendBufferSize(int tcpSendBufferSize) {
        this.serverConfig.setTcpSendBufferSize(tcpSendBufferSize);
        return this;
    }

    public ServerBuilder setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
        this.serverConfig.setTcpReceiveBufferSize(tcpReceiveBufferSize);
        return this;
    }

    public ServerBuilder setTcpNoDelay(boolean tcpNoDelay) {
        this.serverConfig.setTcpNodelay(tcpNoDelay);
        return this;
    }

    public ServerBuilder setReuseAddr(boolean reuseAddr) {
        this.serverConfig.setReuseAddr(reuseAddr);
        return this;
    }

    public ServerBuilder setBacklogSize(int backlogSize) {
        this.serverConfig.setBackLogSize(backlogSize);
        return this;
    }

    public ServerBuilder setMaxChunkSize(int maxChunkSize) {
        this.serverConfig.setMaxChunkSize(maxChunkSize);
        return this;
    }

    public ServerBuilder setMaxInitialLineLength(int maxInitialLineLength) {
        this.serverConfig.setMaxInitialLineLength(maxInitialLineLength);
        return this;
    }

    public ServerBuilder setMaxHeadersSize(int maxHeadersSize) {
        this.serverConfig.setMaxHeadersSize(maxHeadersSize);
        return this;
    }

    public ServerBuilder setMaxContentLength(int maxContentLength) {
        this.serverConfig.setMaxContentLength(maxContentLength);
        return this;
    }

    public ServerBuilder setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
        this.serverConfig.setMaxCompositeBufferComponents(maxCompositeBufferComponents);
        return this;
    }

    public ServerBuilder setReadTimeoutMillis(int readTimeoutMillis) {
        this.serverConfig.setReadTimeoutMillis(readTimeoutMillis);
        return this;
    }

    public ServerBuilder setConnectionTimeoutMillis(int connectionTimeoutMillis) {
        this.serverConfig.setConnectTimeoutMillis(connectionTimeoutMillis);
        return this;
    }

    public ServerBuilder setIdleTimeoutMillis(int idleTimeoutMillis) {
        this.serverConfig.setIdleTimeoutMillis(idleTimeoutMillis);
        return this;
    }

    public ServerBuilder setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        this.serverConfig.setWriteBufferWaterMark(writeBufferWaterMark);
        return this;
    }

    public ServerBuilder setEnableGzip(boolean enableGzip) {
        this.serverConfig.setEnableGzip(enableGzip);
        return this;
    }

    public ServerBuilder setInstallHttp2Upgrade(boolean installHttp2Upgrade) {
        this.serverConfig.setInstallHttp2Upgrade(installHttp2Upgrade);
        return this;
    }

    public ServerBuilder setSslProvider(SslProvider sslProvider) {
        this.serverConfig.setSslProvider(sslProvider);
        return this;
    }

    public ServerBuilder setJdkSslProvider() {
        this.serverConfig.setSslProvider(SslProvider.JDK);
        return this;
    }

    public ServerBuilder setOpenSSLSslProvider() {
        this.serverConfig.setSslProvider(SslProvider.OPENSSL);
        return this;
    }

    public ServerBuilder setCiphers(Iterable<String> ciphers) {
        this.serverConfig.setCiphers(ciphers);
        return this;
    }

    public ServerBuilder setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
        this.serverConfig.setCipherSuiteFilter(cipherSuiteFilter);
        return this;
    }

    public ServerBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        this.serverConfig.setKeyCertChainInputStream(keyCertChainInputStream);
        this.serverConfig.setKeyInputStream(keyInputStream);
        return this;
    }

    public ServerBuilder setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                    String keyPassword) {
        this.serverConfig.setKeyCertChainInputStream(keyCertChainInputStream);
        this.serverConfig.setKeyInputStream(keyInputStream);
        this.serverConfig.setKeyPassword(keyPassword);
        return this;
    }

    public ServerBuilder setSelfCert() throws Exception {
        TrustManagerFactory trustManagerFactory = InsecureTrustManagerFactory.INSTANCE;
        this.serverConfig.setTrustManagerFactory(trustManagerFactory);
        String hostName = serverConfig.getAddress().getInetSocketAddress().getHostString();
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate(hostName);
        this.serverConfig.setKeyCertChainInputStream(selfSignedCertificate.certificate());
        this.serverConfig.setKeyInputStream(selfSignedCertificate.privateKey());
        this.serverConfig.setKeyPassword(null);
        return this;
    }

    public ServerBuilder addVirtualServer(VirtualServer virtualServer) {
        this.serverConfig.addVirtualServer(virtualServer);
        return this;
    }

    public Server build() throws SSLException {
        return new Server(serverConfig, byteBufAllocator, parentEventLoopGroup, childEventLoopGroup, socketChannelClass);
    }
}
