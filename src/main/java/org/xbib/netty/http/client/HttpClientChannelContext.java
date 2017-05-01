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

import io.netty.channel.pool.ChannelPool;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.AttributeKey;

import java.io.InputStream;
import javax.net.ssl.TrustManagerFactory;

/**
 */
final class HttpClientChannelContext {

    static final AttributeKey<ChannelPool> CHANNEL_POOL_ATTRIBUTE_KEY =
            AttributeKey.valueOf("httpClientChannelPool");

    static final AttributeKey<HttpRequestContext> REQUEST_CONTEXT_ATTRIBUTE_KEY =
            AttributeKey.valueOf("httpClientRequestContext");

    static final AttributeKey<HttpResponseListener> RESPONSE_LISTENER_ATTRIBUTE_KEY =
            AttributeKey.valueOf("httpClientResponseListener");

    static final AttributeKey<ExceptionListener> EXCEPTION_LISTENER_ATTRIBUTE_KEY =
            AttributeKey.valueOf("httpClientExceptionListener");

    private final int maxInitialLineLength;

    private final int maxHeaderSize;

    private final int maxChunkSize;

    private final int maxContentLength;

    private final int maxCompositeBufferComponents;

    private final int readTimeoutMillis;

    private final boolean enableGzip;

    private final boolean installHttp2Upgrade;

    private final SslProvider sslProvider;

    private final Iterable<String> ciphers;

    private final CipherSuiteFilter cipherSuiteFilter;

    private final TrustManagerFactory trustManagerFactory;

    private final InputStream keyCertChainInputStream;

    private final InputStream keyInputStream;

    private final String keyPassword;

    private final boolean useServerNameIdentification;

    private final SslClientAuthMode sslClientAuthMode;

    private final HttpProxyHandler httpProxyHandler;

    private final Socks4ProxyHandler socks4ProxyHandler;

    private final Socks5ProxyHandler socks5ProxyHandler;

    HttpClientChannelContext(int maxInitialLineLength,
                             int maxHeaderSize,
                             int maxChunkSize,
                             int maxContentLength,
                             int maxCompositeBufferComponents,
                             int readTimeoutMillis,
                             boolean enableGzip,
                             boolean installHttp2Upgrade,
                             SslProvider sslProvider,
                             Iterable<String> ciphers,
                             CipherSuiteFilter cipherSuiteFilter,
                             TrustManagerFactory trustManagerFactory,
                             InputStream keyCertChainInputStream,
                             InputStream keyInputStream,
                             String keyPassword,
                             boolean useServerNameIdentification,
                             SslClientAuthMode sslClientAuthMode,
                             HttpProxyHandler httpProxyHandler,
                             Socks4ProxyHandler socks4ProxyHandler,
                             Socks5ProxyHandler socks5ProxyHandler) {
        this.maxInitialLineLength = maxInitialLineLength;
        this.maxHeaderSize = maxHeaderSize;
        this.maxChunkSize = maxChunkSize;
        this.maxContentLength = maxContentLength;
        this.maxCompositeBufferComponents = maxCompositeBufferComponents;
        this.readTimeoutMillis = readTimeoutMillis;
        this.enableGzip = enableGzip;
        this.installHttp2Upgrade = installHttp2Upgrade;
        this.sslProvider = sslProvider;
        this.ciphers = ciphers;
        this.cipherSuiteFilter = cipherSuiteFilter;
        this.trustManagerFactory = trustManagerFactory;
        this.keyCertChainInputStream = keyCertChainInputStream;
        this.keyInputStream = keyInputStream;
        this.keyPassword = keyPassword;
        this.useServerNameIdentification = useServerNameIdentification;
        this.sslClientAuthMode = sslClientAuthMode;
        this.httpProxyHandler = httpProxyHandler;
        this.socks4ProxyHandler = socks4ProxyHandler;
        this.socks5ProxyHandler = socks5ProxyHandler;
    }

    int getMaxInitialLineLength() {
        return maxInitialLineLength;
    }

    int getMaxHeaderSize() {
        return maxHeaderSize;
    }

    int getMaxChunkSize() {
        return maxChunkSize;
    }

    int getMaxContentLength() {
        return maxContentLength;
    }

    int getMaxCompositeBufferComponents() {
        return maxCompositeBufferComponents;
    }

    int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    boolean isGzipEnabled() {
        return enableGzip;
    }

    boolean isInstallHttp2Upgrade() {
        return installHttp2Upgrade;
    }

    SslProvider getSslProvider() {
        return sslProvider;
    }

    Iterable<String> getCiphers() {
        return ciphers;
    }

    CipherSuiteFilter getCipherSuiteFilter() {
        return cipherSuiteFilter;
    }

    TrustManagerFactory getTrustManagerFactory() {
        return trustManagerFactory;
    }

    InputStream getKeyCertChainInputStream() {
        return keyCertChainInputStream;
    }

    InputStream getKeyInputStream() {
        return keyInputStream;
    }

    String getKeyPassword() {
        return keyPassword;
    }

    boolean isUseServerNameIdentification() {
        return useServerNameIdentification;
    }

    SslClientAuthMode getSslClientAuthMode() {
        return sslClientAuthMode;
    }

    HttpProxyHandler getHttpProxyHandler() {
        return httpProxyHandler;
    }

    Socks4ProxyHandler getSocks4ProxyHandler() {
        return socks4ProxyHandler;
    }

    Socks5ProxyHandler getSocks5ProxyHandler() {
        return socks5ProxyHandler;
    }
}
