package org.xbib.netty.http.client;

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;

import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.util.List;

public class ClientConfig {

    interface Defaults {

        /**
         * If frame logging /traffic logging is enabled or not.
         */
        boolean DEBUG = false;

        /**
         * Default for thread count.
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
         * See {@link io.netty.handler.codec.MessageAggregator#DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
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
        SslProvider SSL_PROVIDER = SslProvider.JDK;

        /**
         * Default SSL context provider (for JDK SSL only).
         */
        Provider SSL_CONTEXT_PROVIDER = null;

        /**
         * Default ciphers. We care about HTTP/2.
         */
        Iterable<String> CIPHERS = Http2SecurityUtil.CIPHERS;

        /**
         * Default cipher suite filter.
         */
        CipherSuiteFilter CIPHER_SUITE_FILTER = SupportedCipherSuiteFilter.INSTANCE;

        boolean USE_SERVER_NAME_IDENTIFICATION = true;

        /**
         * Default for SSL client authentication.
         */
        ClientAuthMode SSL_CLIENT_AUTH_MODE = ClientAuthMode.NONE;
    }

    private static TrustManagerFactory TRUST_MANAGER_FACTORY;

    static {
        try {
            TRUST_MANAGER_FACTORY = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    //InsecureTrustManagerFactory.INSTANCE;
            //TRUST_MANAGER_FACTORY.init((KeyStore) null);
            // java.lang.IllegalStateException: TrustManagerFactoryImpl is not initialized
                    //TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        } catch (Exception e) {
            TRUST_MANAGER_FACTORY = null;
        }
    }

    private boolean debug = Defaults.DEBUG;

    /**
     * If set to 0, then Netty will decide about thread count.
     * Default is Runtime.getRuntime().availableProcessors() * 2
     */
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

    private Iterable<String> ciphers = Defaults.CIPHERS;

    private CipherSuiteFilter cipherSuiteFilter = Defaults.CIPHER_SUITE_FILTER;

    private TrustManagerFactory trustManagerFactory = TRUST_MANAGER_FACTORY;

    private KeyStore trustManagerKeyStore = null;

    private boolean serverNameIdentification = Defaults.USE_SERVER_NAME_IDENTIFICATION;

    private ClientAuthMode clientAuthMode = Defaults.SSL_CLIENT_AUTH_MODE;

    private InputStream keyCertChainInputStream;

    private InputStream keyInputStream;

    private String keyPassword;

    private HttpProxyHandler httpProxyHandler;

    private List<HttpAddress> nodes;

    private Integer nodeConnectionLimit;

    private Integer retriesPerNode;

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

    public ClientConfig setServerNameIdentification(boolean serverNameIdentification) {
        this.serverNameIdentification = serverNameIdentification;
        return this;
    }

    public boolean isServerNameIdentification() {
        return serverNameIdentification;
    }

    public ClientConfig setClientAuthMode(ClientAuthMode clientAuthMode) {
        this.clientAuthMode = clientAuthMode;
        return this;
    }

    public ClientAuthMode getClientAuthMode() {
        return clientAuthMode;
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

    public ClientConfig setHttpProxyHandler(HttpProxyHandler httpProxyHandler) {
        this.httpProxyHandler = httpProxyHandler;
        return this;
    }

    public HttpProxyHandler getHttpProxyHandler() {
        return httpProxyHandler;
    }

    public ClientConfig setNodes(List<HttpAddress> nodes) {
        this.nodes = nodes;
        return this;
    }

    public List<HttpAddress> getNodes() {
        return nodes;
    }

    public ClientConfig setNodeConnectionLimit(Integer nodeConnectionLimit) {
        this.nodeConnectionLimit = nodeConnectionLimit;
        return this;
    }

    public Integer getNodeConnectionLimit() {
        return nodeConnectionLimit;
    }

    public ClientConfig setRetriesPerNode(Integer retriesPerNode) {
        this.retriesPerNode = retriesPerNode;
        return this;
    }

    public Integer getRetriesPerNode() {
        return retriesPerNode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SSL=").append(sslProvider)
                .append(",SSL context provider=").append(sslContextProvider != null ? sslContextProvider.getName() : "<none>");
        return sb.toString();


    }
}
