package org.xbib.netty.http.server.api;

import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.security.SecurityUtil;

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

    boolean isDecompressionEnabled();

    boolean isPipeliningEnabled();

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

    SimpleChannelInboundHandler<WebSocketFrame> getWebSocketFrameHandler();

    interface Defaults {

        /**
         * Default bind address. We do not want to use port 80 or 8080.
         */
        HttpAddress ADDRESS = HttpAddress.http1("localhost", 8008);

        /**
         * If frame logging/traffic logging is enabled or not.
         */
        boolean DEBUG = false;

        /**
         * Default debug log level.
         */
        LogLevel DEBUG_LOG_LEVEL = LogLevel.DEBUG;

        String TRANSPORT_PROVIDER_NAME = null;

        /**
         * Let Netty decide about parent thread count.
         */
        int PARENT_THREAD_COUNT = 0;

        /**
         * Let Netty decide about child thread count.
         */
        int CHILD_THREAD_COUNT = 0;

        /**
         * Blocking thread pool count. Disabled by default, use Netty threads.
         */
        int BLOCKING_THREAD_COUNT = 0;

        /**
         * Blocking thread pool queue count. Disabled by default, use Netty threads.
         */
        int BLOCKING_QUEUE_COUNT = 0;

        /**
         * Default for SO_REUSEADDR.
         */
        boolean SO_REUSEADDR = true;

        /**
         * Default for TCP_NODELAY.
         */
        boolean TCP_NODELAY = true;

        /**
         * Set TCP send buffer to 64k per socket.
         */
        int TCP_SEND_BUFFER_SIZE = 64 * 1024;

        /**
         * Set TCP receive buffer to 64k per socket.
         */
        int TCP_RECEIVE_BUFFER_SIZE = 64 * 1024;

        /**
         * Default for socket back log.
         */
        int SO_BACKLOG = 10 * 1024;

        /**
         * Default connect timeout in milliseconds.
         */
        int CONNECT_TIMEOUT_MILLIS = 5000;

        /**
         * Default connect timeout in milliseconds.
         */
        int READ_TIMEOUT_MILLIS = 15000;

        /**
         * Default idle timeout in milliseconds.
         */
        int IDLE_TIMEOUT_MILLIS = 60000;

        /**
         * Set HTTP chunk maximum size to 8k.
         * See {@link io.netty.handler.codec.http.HttpClientCodec}.
         */
        int MAX_CHUNK_SIZE = 8 * 1024;

        /**
         * Set HTTP initial line length to 4k.
         * See {@link io.netty.handler.codec.http.HttpClientCodec}.
         */
        int MAX_INITIAL_LINE_LENGTH = 4 * 1024;

        /**
         * Set HTTP maximum headers size to 8k.
         * See  {@link io.netty.handler.codec.http.HttpClientCodec}.
         */
        int MAX_HEADERS_SIZE = 8 * 1024;

        /**
         * Set maximum content length to 256 MB.
         */
        int MAX_CONTENT_LENGTH = 256 * 1024 * 1024;

        /**
         * HTTP/1 pipelining. Enabled by default.
         */
        boolean ENABLE_PIPELINING = true;
        /**
         * HTTP/1 pipelining capacity. 1024 is very high, it means
         * 1024 requests can be present for a single client.
         */
        int PIPELINING_CAPACITY = 1024;

        /**
         * This is Netty's default.
         */
        int MAX_COMPOSITE_BUFFER_COMPONENTS = 1024;

        /**
         * Default write buffer water mark.
         */
        WriteBufferWaterMark WRITE_BUFFER_WATER_MARK = WriteBufferWaterMark.DEFAULT;

        /**
         * Default for compression.
         */
        boolean ENABLE_COMPRESSION = true;

        /**
         * Default for decompression.
         */
        boolean ENABLE_DECOMPRESSION = true;

        /**
         * Default HTTP/2 settings.
         */
        Http2Settings HTTP_2_SETTINGS = Http2Settings.defaultSettings();

        /**
         * Default for HTTP/2 upgrade under HTTP 1.
         */
        boolean INSTALL_HTTP_UPGRADE2 = false;

        /**
         * Default SSL provider.
         */
        SslProvider SSL_PROVIDER = SecurityUtil.Defaults.DEFAULT_SSL_PROVIDER;

        /**
         * Default SSL context provider (for JDK SSL only).
         */
        Provider SSL_CONTEXT_PROVIDER = null;

        /**
         * Transport layer security protocol versions.
         * Do not use SSLv2, SSLv3, TLS 1.0, TLS 1.1.
         */
        String[] PROTOCOLS = OpenSsl.isAvailable() && OpenSsl.version() <= 0x10101009L ?
                new String[] { "TLSv1.2" } :
                new String[] { "TLSv1.3", "TLSv1.2" };

        /**
         * Default ciphers. We care about HTTP/2.
         */
        Iterable<String> CIPHERS = SecurityUtil.Defaults.DEFAULT_CIPHERS;

        /**
         * Default cipher suite filter.
         */
        CipherSuiteFilter CIPHER_SUITE_FILTER = SecurityUtil.Defaults.DEFAULT_CIPHER_SUITE_FILTER;

    }
}
