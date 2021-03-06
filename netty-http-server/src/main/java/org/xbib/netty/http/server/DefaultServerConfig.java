package org.xbib.netty.http.server;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.security.SecurityUtil;
import org.xbib.netty.http.server.api.Domain;
import org.xbib.netty.http.server.api.EndpointResolver;
import org.xbib.netty.http.server.api.ServerConfig;
import java.security.KeyStore;
import java.security.Provider;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;
import javax.net.ssl.TrustManagerFactory;

public class DefaultServerConfig implements ServerConfig {

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
         * HTTP/1 pipelining capacity. 1024 is very high, it means
         * 1024 requests can be present for a single client.
         */
        int PIPELINING_CAPACITY = 1024;

        /**
         * This is Netty's default.
         * See {@link io.netty.handler.codec.MessageAggregator#maxCumulationBufferComponents()}.
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
         * Default compression threshold. If a response size is over this value,
         * it will be compressed, otherwise not.
         */
        int COMPRESSION_THRESHOLD = 8192;

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
        String[] PROTOCOLS = new String[] { "TLSv1.3", "TLSv1.2" };

        /**
         * Default ciphers. We care about HTTP/2.
         */
        Iterable<String> CIPHERS = SecurityUtil.Defaults.DEFAULT_CIPHERS;

        /**
         * Default cipher suite filter.
         */
        CipherSuiteFilter CIPHER_SUITE_FILTER = SecurityUtil.Defaults.DEFAULT_CIPHER_SUITE_FILTER;

    }

    private HttpAddress httpAddress = Defaults.ADDRESS;

    private boolean debug = Defaults.DEBUG;

    private LogLevel debugLogLevel = Defaults.DEBUG_LOG_LEVEL;

    private String transportProviderName = Defaults.TRANSPORT_PROVIDER_NAME;

    private int parentThreadCount = Defaults.PARENT_THREAD_COUNT;

    private int childThreadCount = Defaults.CHILD_THREAD_COUNT;

    private int blockingThreadCount = Defaults.BLOCKING_THREAD_COUNT;

    private int blockingQueueCount = Defaults.BLOCKING_QUEUE_COUNT;

    private boolean reuseAddr = Defaults.SO_REUSEADDR;

    private boolean tcpNodelay = Defaults.TCP_NODELAY;

    private int tcpSendBufferSize = Defaults.TCP_SEND_BUFFER_SIZE;

    private int tcpReceiveBufferSize = Defaults.TCP_RECEIVE_BUFFER_SIZE;

    private int backLogSize = Defaults.SO_BACKLOG;

    private int maxInitialLineLength = Defaults.MAX_INITIAL_LINE_LENGTH;

    private int maxHeadersSize = Defaults.MAX_HEADERS_SIZE;

    private int maxChunkSize = Defaults.MAX_CHUNK_SIZE;

    private int maxContentLength = Defaults.MAX_CONTENT_LENGTH;

    private int pipeliningCapacity = Defaults.PIPELINING_CAPACITY;

    private int maxCompositeBufferComponents = Defaults.MAX_COMPOSITE_BUFFER_COMPONENTS;

    private int connectTimeoutMillis = Defaults.CONNECT_TIMEOUT_MILLIS;

    private int readTimeoutMillis = Defaults.READ_TIMEOUT_MILLIS;

    private int idleTimeoutMillis = Defaults.IDLE_TIMEOUT_MILLIS;

    private WriteBufferWaterMark writeBufferWaterMark = Defaults.WRITE_BUFFER_WATER_MARK;

    private boolean enableCompression = Defaults.ENABLE_COMPRESSION;

    private int compressionThreshold = Defaults.COMPRESSION_THRESHOLD;

    private boolean enableDecompression = Defaults.ENABLE_DECOMPRESSION;

    private Http2Settings http2Settings = Defaults.HTTP_2_SETTINGS;

    private boolean installHttp2Upgrade = Defaults.INSTALL_HTTP_UPGRADE2;

    private final Deque<Domain<? extends EndpointResolver<?>>> domains;

    private SslProvider sslProvider = Defaults.SSL_PROVIDER;

    private Provider sslContextProvider = Defaults.SSL_CONTEXT_PROVIDER;

    private String[] protocols = Defaults.PROTOCOLS;

    private Iterable<String> ciphers = Defaults.CIPHERS;

    private CipherSuiteFilter cipherSuiteFilter = Defaults.CIPHER_SUITE_FILTER;

    private TrustManagerFactory trustManagerFactory = SecurityUtil.Defaults.DEFAULT_TRUST_MANAGER_FACTORY;

    private KeyStore trustManagerKeyStore = null;

    private boolean autoDomain = false;

    private boolean acceptInvalidCertificates = false;

    public DefaultServerConfig() {
        this.domains = new LinkedList<>();
    }

    public ServerConfig setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    public ServerConfig setDebugLogLevel(LogLevel debugLogLevel) {
        this.debugLogLevel = debugLogLevel;
        return this;
    }

    public LogLevel getTrafficDebugLogLevel() {
        return debugLogLevel;
    }

    public ServerConfig setTransportProviderName(String transportProviderName) {
        this.transportProviderName = transportProviderName;
        return this;
    }

    public String getTransportProviderName() {
        return transportProviderName;
    }

    public ServerConfig setParentThreadCount(int parentThreadCount) {
        this.parentThreadCount = parentThreadCount;
        return this;
    }

    public int getParentThreadCount() {
        return parentThreadCount;
    }

    public ServerConfig setChildThreadCount(int childThreadCount) {
        this.childThreadCount = childThreadCount;
        return this;
    }

    public int getChildThreadCount() {
        return childThreadCount;
    }

    public ServerConfig setBlockingThreadCount(int blockingThreadCount) {
        this.blockingThreadCount = blockingThreadCount;
        return this;
    }

    public int getBlockingThreadCount() {
        return blockingThreadCount;
    }

    public ServerConfig setBlockingQueueCount(int blockingQueueCount) {
        this.blockingQueueCount = blockingQueueCount;
        return this;
    }

    public int getBlockingQueueCount() {
        return blockingQueueCount;
    }

    public ServerConfig setReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
        return this;
    }

    public boolean isReuseAddr() {
        return reuseAddr;
    }

    public ServerConfig setTcpNodelay(boolean tcpNodelay) {
        this.tcpNodelay = tcpNodelay;
        return this;
    }

    public boolean isTcpNodelay() {
        return tcpNodelay;
    }

    public ServerConfig setTcpSendBufferSize(int tcpSendBufferSize) {
        this.tcpSendBufferSize = tcpSendBufferSize;
        return this;
    }

    public int getTcpSendBufferSize() {
        return tcpSendBufferSize;
    }

    public ServerConfig setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
        this.tcpReceiveBufferSize = tcpReceiveBufferSize;
        return this;
    }

    public int getTcpReceiveBufferSize() {
        return tcpReceiveBufferSize;
    }

    public ServerConfig setBackLogSize(int backLogSize) {
        this.backLogSize = backLogSize;
        return this;
    }

    public int getBackLogSize() {
        return backLogSize;
    }

    public ServerConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public ServerConfig setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
        return this;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public ServerConfig setIdleTimeoutMillis(int idleTimeoutMillis) {
        this.idleTimeoutMillis = idleTimeoutMillis;
        return this;
    }

    public int getIdleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    public ServerConfig setAddress(HttpAddress httpAddress) {
        this.httpAddress = httpAddress;
        return this;
    }

    public HttpAddress getAddress() {
        return httpAddress;
    }

    public ServerConfig setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public ServerConfig setMaxHeadersSize(int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
        return this;
    }

    public int getMaxHeadersSize() {
        return maxHeadersSize;
    }

    public ServerConfig setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public ServerConfig setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public ServerConfig setPipeliningCapacity(int pipeliningCapacity) {
        this.pipeliningCapacity = pipeliningCapacity;
        return this;
    }

    public int getPipeliningCapacity() {
        return pipeliningCapacity;
    }

    public ServerConfig setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
        this.maxCompositeBufferComponents = maxCompositeBufferComponents;
        return this;
    }

    public int getMaxCompositeBufferComponents() {
        return maxCompositeBufferComponents;
    }

    public ServerConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        this.writeBufferWaterMark = writeBufferWaterMark;
        return this;
    }

    public WriteBufferWaterMark getWriteBufferWaterMark() {
        return writeBufferWaterMark;
    }

    public ServerConfig setCompression(boolean enabled) {
        this.enableCompression = enabled;
        return this;
    }

    public boolean isCompressionEnabled() {
        return enableCompression;
    }

    public ServerConfig setCompressionThreshold(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
        return this;
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public ServerConfig setDecompression(boolean enabled) {
        this.enableDecompression = enabled;
        return this;
    }

    public boolean isDecompressionEnabled() {
        return enableDecompression;
    }

    public ServerConfig setInstallHttp2Upgrade(boolean http2Upgrade) {
        this.installHttp2Upgrade = http2Upgrade;
        return this;
    }

    public boolean isInstallHttp2Upgrade() {
        return installHttp2Upgrade;
    }

    public ServerConfig setHttp2Settings(Http2Settings http2Settings) {
        this.http2Settings = http2Settings;
        return this;
    }

    public Http2Settings getHttp2Settings() {
        return http2Settings;
    }

    public ServerConfig setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    public TrustManagerFactory getTrustManagerFactory() {
        return trustManagerFactory;
    }

    public ServerConfig setTrustManagerKeyStore(KeyStore trustManagerKeyStore) {
        this.trustManagerKeyStore = trustManagerKeyStore;
        return this;
    }

    public KeyStore getTrustManagerKeyStore() {
        return trustManagerKeyStore;
    }

    public ServerConfig setSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
        return this;
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    public ServerConfig setJdkSslProvider() {
        this.sslProvider = SslProvider.JDK;
        return this;
    }

    public ServerConfig setOpenSSLSslProvider() {
        this.sslProvider = SslProvider.OPENSSL;
        return this;
    }

    public ServerConfig setSslContextProvider(Provider sslContextProvider) {
        this.sslContextProvider = sslContextProvider;
        return this;
    }

    public Provider getSslContextProvider() {
        return sslContextProvider;
    }

    public ServerConfig setProtocols(String[] protocols) {
        this.protocols = protocols;
        return this;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public ServerConfig setCiphers(Iterable<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    public Iterable<String> getCiphers() {
        return ciphers;
    }

    public ServerConfig setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
        this.cipherSuiteFilter = cipherSuiteFilter;
        return this;
    }

    public CipherSuiteFilter getCipherSuiteFilter() {
        return cipherSuiteFilter;
    }

    public ServerConfig setAutoDomain(boolean autoDomain) {
        this.autoDomain = autoDomain;
        return this;
    }

    public boolean isAutoDomain() {
        return autoDomain;
    }

    public ServerConfig setAcceptInvalidCertificates(boolean acceptInvalidCertificates) {
        this.acceptInvalidCertificates = acceptInvalidCertificates;
        return this;
    }

    public boolean isAcceptInvalidCertificates() {
        return acceptInvalidCertificates;
    }

    public ServerConfig addDomain(Domain<? extends EndpointResolver<?>> domain) {
        domains.add(domain);
        return this;
    }

    @Override
    public Collection<Domain<? extends EndpointResolver<?>>> getDomains() {
        return domains;
    }

    @Override
    public Domain<? extends EndpointResolver<?>> getDomain(String name) {
        Optional<Domain<? extends EndpointResolver<?>>> domainOptional =
                domains.stream().filter(d -> d.getName().equals(name)).findFirst();
        return domainOptional.orElse(domains.getFirst());
    }

    @Override
    public Domain<? extends EndpointResolver<?>> getDefaultDomain() {
        Optional<Domain<? extends EndpointResolver<?>>> domainOptional =
                domains.stream().filter(d -> d.getName().equals("*")).findFirst();
        return domainOptional.orElse(domains.getFirst());
    }
}
