package org.xbib.netty.http.server.api;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import java.security.KeyStore;
import java.security.Provider;
import java.util.Collection;
import javax.net.ssl.TrustManagerFactory;

public interface ServerConfig {

    boolean isDebug();

    LogLevel getTrafficDebugLogLevel();

    String getTransportProviderName();

    int getParentThreadCount();

    int getChildThreadCount();

    int getBlockingThreadCount();

    int getBlockingQueueCount();

    boolean isReuseAddr();

    boolean isTcpNodelay();

    int getTcpSendBufferSize();

    int getTcpReceiveBufferSize();

    int getBackLogSize();

    int getConnectTimeoutMillis();

    int getReadTimeoutMillis();

    int getIdleTimeoutMillis();

    HttpAddress getAddress();

    int getMaxInitialLineLength();

    int getMaxHeadersSize();

    int getMaxChunkSize();

    int getMaxContentLength();

    int getPipeliningCapacity();

    int getMaxCompositeBufferComponents();

    WriteBufferWaterMark getWriteBufferWaterMark();

    boolean isCompressionEnabled();

    int getCompressionThreshold();

    boolean isDecompressionEnabled();

    boolean isInstallHttp2Upgrade();

    Http2Settings getHttp2Settings();

    TrustManagerFactory getTrustManagerFactory();

    KeyStore getTrustManagerKeyStore();

    SslProvider getSslProvider();

    Provider getSslContextProvider();

    String[] getProtocols();

    Iterable<String> getCiphers();

    CipherSuiteFilter getCipherSuiteFilter();

    boolean isAutoDomain();

    boolean isAcceptInvalidCertificates();

    Collection<Domain<? extends EndpointResolver<?>>> getDomains();

    Domain<? extends EndpointResolver<?>> getDomain(String name);

    Domain<? extends EndpointResolver<?>> getDefaultDomain();
}
