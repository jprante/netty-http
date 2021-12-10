package org.xbib.netty.http.client;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslProvider;
import org.xbib.netty.http.client.api.Pool;
import org.xbib.netty.http.client.api.BackOff;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.security.SecurityUtil;

import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.util.ArrayList;
import java.util.List;

public class ClientConfig {

    interface Defaults {

        /**
         * If frame logging /traffic logging is enabled or not.
         */
        boolean DEBUG = false;

        /**
         * Default debug log level.
         */
        LogLevel DEFAULT_DEBUG_LOG_LEVEL = LogLevel.DEBUG;

        /**
         * The transport provider
         */
        String DEFAULT_TRANSPORT_PROVIDER = null;

        /**
         * If set to 0, then Netty will decide about thread count.
         * Default is Runtime.getRuntime().availableProcessors() * 2
         */
        int THREAD_COUNT = 0;

        /**
         * Default for TCP_NODELAY.
         */
        boolean TCP_NODELAY = true;

        /**
         * Default for SO_KEEPALIVE.
         */
        boolean SO_KEEPALIVE = true;

        /**
         * Default for SO_REUSEADDR.
         */
        boolean SO_REUSEADDR = true;

        /**
         * Set TCP send buffer to 64k per socket.
         */
        int TCP_SEND_BUFFER_SIZE = 64 * 1024;

        /**
         * Set TCP receive buffer to 64k per socket.
         */
        int TCP_RECEIVE_BUFFER_SIZE = 64 * 1024;

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
         * Set maximum content length to 100 MB.
         */
        int MAX_CONTENT_LENGTH = 100 * 1024 * 1024;

        /**
         * This is Netty's default.
         */
        int MAX_COMPOSITE_BUFFER_COMPONENTS = 1024;

        /**
         * Default read/write timeout in milliseconds.
         */
        int TIMEOUT_MILLIS = 5000;

        /**
         * Default for gzip codec.
         */
        boolean ENABLE_GZIP = true;

        /**
         * Default SSL provider.
         */
        SslProvider SSL_PROVIDER = SecurityUtil.Defaults.DEFAULT_SSL_PROVIDER;

        /**
         * Default SSL context provider (for JDK SSL only).
         */
        Provider SSL_CONTEXT_PROVIDER = null;

        /**
         * Default transport layer security protocol versions (depends on OpenSSL version)
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

        /**
         * Default for SSL client authentication.
         */
        ClientAuthMode SSL_CLIENT_AUTH_MODE = ClientAuthMode.NONE;

        /**
         * Default for pool retries per node.
         */
        Integer RETRIES_PER_NODE = 0;

        /**
         * Default pool HTTP version.
         */
        HttpVersion POOL_VERSION = HttpVersion.HTTP_1_1;

        Pool.PoolKeySelectorType POOL_KEY_SELECTOR_TYPE = Pool.PoolKeySelectorType.ROUNDROBIN;

        /**
         * Default connection pool security.
         */
        Boolean POOL_SECURE = false;

        /**
         * Default HTTP/2 settings.
         */
        Http2Settings HTTP2_SETTINGS = Http2Settings.defaultSettings();

        /**
         * Default write buffer water mark.
         */
        WriteBufferWaterMark WRITE_BUFFER_WATER_MARK = WriteBufferWaterMark.DEFAULT;

        /**
         * Default for backoff.
         */
        BackOff BACK_OFF = BackOff.ZERO_BACKOFF;

        Boolean ENABLE_NEGOTIATION = false;
    }

    private boolean debug = Defaults.DEBUG;

    private LogLevel debugLogLevel = Defaults.DEFAULT_DEBUG_LOG_LEVEL;

    private String transportProviderName = Defaults.DEFAULT_TRANSPORT_PROVIDER;

    private int threadCount = Defaults.THREAD_COUNT;

    private boolean tcpNodelay = Defaults.TCP_NODELAY;

    private boolean keepAlive = Defaults.SO_KEEPALIVE;

    private boolean reuseAddr = Defaults.SO_REUSEADDR;

    private int tcpSendBufferSize = Defaults.TCP_SEND_BUFFER_SIZE;

    private int tcpReceiveBufferSize = Defaults.TCP_RECEIVE_BUFFER_SIZE;

    private int maxInitialLineLength = Defaults.MAX_INITIAL_LINE_LENGTH;

    private int maxHeadersSize = Defaults.MAX_HEADERS_SIZE;

    private int maxChunkSize = Defaults.MAX_CHUNK_SIZE;

    private int maxContentLength = Defaults.MAX_CONTENT_LENGTH;

    private int maxCompositeBufferComponents = Defaults.MAX_COMPOSITE_BUFFER_COMPONENTS;

    private int connectTimeoutMillis = Defaults.TIMEOUT_MILLIS;

    private int readTimeoutMillis = Defaults.TIMEOUT_MILLIS;

    private boolean enableGzip = Defaults.ENABLE_GZIP;

    private SslProvider sslProvider = Defaults.SSL_PROVIDER;

    private Provider sslContextProvider = Defaults.SSL_CONTEXT_PROVIDER;

    private String[] protocols =  Defaults.PROTOCOLS;

    private Iterable<String> ciphers = Defaults.CIPHERS;

    private CipherSuiteFilter cipherSuiteFilter = Defaults.CIPHER_SUITE_FILTER;

    private TrustManagerFactory trustManagerFactory = SecurityUtil.Defaults.DEFAULT_TRUST_MANAGER_FACTORY;

    private KeyStore trustManagerKeyStore = null;

    private ClientAuthMode clientAuthMode = Defaults.SSL_CLIENT_AUTH_MODE;

    private InputStream keyCertChainInputStream;

    private InputStream keyInputStream;

    private String keyPassword;

    private HttpProxyHandler httpProxyHandler;

    private List<HttpAddress> poolNodes = new ArrayList<>();

    private Pool.PoolKeySelectorType poolKeySelectorType = Defaults.POOL_KEY_SELECTOR_TYPE;

    private Integer poolNodeConnectionLimit;

    private Integer retriesPerPoolNode = Defaults.RETRIES_PER_NODE;

    private HttpVersion poolVersion = Defaults.POOL_VERSION;

    private Boolean poolSecure = Defaults.POOL_SECURE;

    private final List<String> serverNamesForIdentification = new ArrayList<>();

    private Http2Settings http2Settings = Defaults.HTTP2_SETTINGS;

    private WriteBufferWaterMark writeBufferWaterMark = Defaults.WRITE_BUFFER_WATER_MARK;

    private BackOff backOff = Defaults.BACK_OFF;

    private boolean enableNegotiation = Defaults.ENABLE_NEGOTIATION;

    public ClientConfig setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public ClientConfig enableDebug() {
        this.debug = true;
        return this;
    }

    public ClientConfig disableDebug() {
        this.debug = false;
        return this;
    }

    public boolean isDebug() {
        return debug;
    }

    public ClientConfig setDebugLogLevel(LogLevel debugLogLevel) {
        this.debugLogLevel = debugLogLevel;
        return this;
    }

    public LogLevel getDebugLogLevel() {
        return debugLogLevel;
    }

    public ClientConfig setTransportProviderName(String transportProviderName) {
        this.transportProviderName = transportProviderName;
        return this;
    }

    public String getTransportProviderName() {
        return transportProviderName;
    }

    public ClientConfig setThreadCount(int threadCount) {
        this.threadCount = threadCount;
        return this;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public ClientConfig setTcpNodelay(boolean tcpNodelay) {
        this.tcpNodelay = tcpNodelay;
        return this;
    }

    public boolean isTcpNodelay() {
        return tcpNodelay;
    }

    public ClientConfig setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
        return this;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public ClientConfig setReuseAddr(boolean reuseAddr) {
        this.reuseAddr = reuseAddr;
        return this;
    }

    public boolean isReuseAddr() {
        return reuseAddr;
    }

    public ClientConfig setTcpSendBufferSize(int tcpSendBufferSize) {
        this.tcpSendBufferSize = tcpSendBufferSize;
        return this;
    }

    public int getTcpSendBufferSize() {
        return tcpSendBufferSize;
    }

    public ClientConfig setTcpReceiveBufferSize(int tcpReceiveBufferSize) {
        this.tcpReceiveBufferSize = tcpReceiveBufferSize;
        return this;
    }

    public int getTcpReceiveBufferSize() {
        return tcpReceiveBufferSize;
    }

    public ClientConfig setMaxInitialLineLength(int maxInitialLineLength) {
        this.maxInitialLineLength = maxInitialLineLength;
        return this;
    }

    public int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    public ClientConfig setMaxHeadersSize(int maxHeadersSize) {
        this.maxHeadersSize = maxHeadersSize;
        return this;
    }

    public int getMaxHeadersSize() {
        return maxHeadersSize;
    }

    public ClientConfig setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        return this;
    }

    public int getMaxChunkSize() {
        return maxChunkSize;
    }

    public ClientConfig setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
        return this;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public ClientConfig setMaxCompositeBufferComponents(int maxCompositeBufferComponents) {
        this.maxCompositeBufferComponents = maxCompositeBufferComponents;
        return this;
    }

    public int getMaxCompositeBufferComponents() {
        return maxCompositeBufferComponents;
    }

    public ClientConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        return this;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public ClientConfig setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
        return this;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public ClientConfig setEnableGzip(boolean enableGzip) {
        this.enableGzip = enableGzip;
        return this;
    }

    public boolean isEnableGzip() {
        return enableGzip;
    }

    public ClientConfig setHttp2Settings(Http2Settings http2Settings) {
        this.http2Settings = http2Settings;
        return this;
    }

    public Http2Settings getHttp2Settings() {
        return http2Settings;
    }

    public ClientConfig setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
        this.trustManagerFactory = trustManagerFactory;
        return this;
    }

    public TrustManagerFactory getTrustManagerFactory() {
        return trustManagerFactory;
    }

    public ClientConfig setTrustManagerKeyStore(KeyStore trustManagerKeyStore) {
        this.trustManagerKeyStore = trustManagerKeyStore;
        return this;
    }

    public KeyStore getTrustManagerKeyStore() {
        return trustManagerKeyStore;
    }

    public ClientConfig setSslProvider(SslProvider sslProvider) {
        this.sslProvider = sslProvider;
        return this;
    }

    public SslProvider getSslProvider() {
        return sslProvider;
    }

    public ClientConfig setJdkSslProvider() {
        this.sslProvider = SslProvider.JDK;
        return this;
    }

    public ClientConfig setOpenSSLSslProvider() {
        this.sslProvider = SslProvider.OPENSSL;
        return this;
    }

    public ClientConfig setSslContextProvider(Provider sslContextProvider) {
        this.sslContextProvider = sslContextProvider;
        return this;
    }

    public Provider getSslContextProvider() {
        return sslContextProvider;
    }

    public ClientConfig setProtocols(String[] protocols) {
        this.protocols = protocols;
        return this;
    }

    public String[] getProtocols() {
        return protocols;
    }

    public ClientConfig setCiphers(Iterable<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }

    public Iterable<String> getCiphers() {
        return ciphers;
    }

    public ClientConfig setCipherSuiteFilter(CipherSuiteFilter cipherSuiteFilter) {
        this.cipherSuiteFilter = cipherSuiteFilter;
        return this;
    }

    public CipherSuiteFilter getCipherSuiteFilter() {
        return cipherSuiteFilter;
    }

    public ClientConfig setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream) {
        this.keyCertChainInputStream = keyCertChainInputStream;
        this.keyInputStream = keyInputStream;
        return this;
    }

    public InputStream getKeyCertChainInputStream() {
        return keyCertChainInputStream;
    }

    public InputStream getKeyInputStream() {
        return keyInputStream;
    }

    public ClientConfig setKeyCert(InputStream keyCertChainInputStream, InputStream keyInputStream,
                                    String keyPassword) {
        this.keyCertChainInputStream = keyCertChainInputStream;
        this.keyInputStream = keyInputStream;
        this.keyPassword = keyPassword;
        return this;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public ClientConfig setClientAuthMode(ClientAuthMode clientAuthMode) {
        this.clientAuthMode = clientAuthMode;
        return this;
    }

    public ClientAuthMode getClientAuthMode() {
        return clientAuthMode;
    }

    public ClientConfig setHttpProxyHandler(HttpProxyHandler httpProxyHandler) {
        this.httpProxyHandler = httpProxyHandler;
        return this;
    }

    public HttpProxyHandler getHttpProxyHandler() {
        return httpProxyHandler;
    }

    public ClientConfig setPoolNodes(List<HttpAddress> poolNodes) {
        this.poolNodes = poolNodes;
        return this;
    }

    public List<HttpAddress> getPoolNodes() {
        return poolNodes;
    }

    public ClientConfig setPoolKeySelectorType(Pool.PoolKeySelectorType poolKeySelectorType) {
        this.poolKeySelectorType = poolKeySelectorType;
        return this;
    }

    public Pool.PoolKeySelectorType getPoolKeySelectorType() {
        return poolKeySelectorType;
    }

    public ClientConfig addPoolNode(HttpAddress poolNodeAddress) {
        this.poolNodes.add(poolNodeAddress);
        return this;
    }

    public ClientConfig setPoolNodeConnectionLimit(Integer poolNodeConnectionLimit) {
        this.poolNodeConnectionLimit = poolNodeConnectionLimit;
        return this;
    }

    public Integer getPoolNodeConnectionLimit() {
        return poolNodeConnectionLimit;
    }

    public ClientConfig setRetriesPerPoolNode(Integer retriesPerPoolNode) {
        this.retriesPerPoolNode = retriesPerPoolNode;
        return this;
    }

    public Integer getRetriesPerPoolNode() {
        return retriesPerPoolNode;
    }

    public ClientConfig setPoolVersion(HttpVersion poolVersion) {
        this.poolVersion = poolVersion;
        return this;
    }

    public HttpVersion getPoolVersion() {
        return  poolVersion;
    }

    public ClientConfig setPoolSecure(boolean poolSecure) {
        this.poolSecure = poolSecure;
        return this;
    }

    public boolean isPoolSecure() {
        return poolSecure;
    }

    public ClientConfig addServerNameForIdentification(String serverNameForIdentification) {
        this.serverNamesForIdentification.add(serverNameForIdentification);
        return this;
    }

    public List<String> getServerNamesForIdentification() {
        return serverNamesForIdentification;
    }

    public ClientConfig setWriteBufferWaterMark(WriteBufferWaterMark writeBufferWaterMark) {
        this.writeBufferWaterMark = writeBufferWaterMark;
        return this;
    }

    public WriteBufferWaterMark getWriteBufferWaterMark() {
        return writeBufferWaterMark;
    }

    public ClientConfig setBackOff(BackOff backOff) {
        this.backOff = backOff;
        return this;
    }

    public BackOff getBackOff() {
        return backOff;
    }

    public ClientConfig setEnableNegotiation(boolean enableNegotiation) {
        this.enableNegotiation = enableNegotiation;
        return this;
    }

    public boolean isEnableNegotiation() {
        return enableNegotiation;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SSL=").append(sslProvider)
                .append(",SSL context provider=").append(sslContextProvider != null ? sslContextProvider.getName() : "<none>");
        return sb.toString();
    }
}
