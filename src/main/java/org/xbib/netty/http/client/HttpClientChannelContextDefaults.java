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

import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.CipherSuiteFilter;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.TrustManagerFactory;

/**
 */
public interface HttpClientChannelContextDefaults {

    /**
     * Default for TCP_NODELAY.
     */
    boolean DEFAULT_TCP_NODELAY = true;

    /**
     * Default for SO_KEEPALIVE.
     */
    boolean DEFAULT_SO_KEEPALIVE = true;

    /**
     * Default for SO_REUSEADDR.
     */
    boolean DEFAULT_SO_REUSEADDR = true;

    /**
     * Set TCP send buffer to 64k per socket.
     */
    int DEFAULT_TCP_SEND_BUFFER_SIZE = 64 * 1024;

    /**
     * Set TCP receive buffer to 64k per socket.
     */
    int DEFAULT_TCP_RECEIVE_BUFFER_SIZE = 64 * 1024;

    /**
     * Set HTTP chunk maximum size to 8k.
     * See {@link io.netty.handler.codec.http.HttpClientCodec}.
     */
    int DEFAULT_MAX_CHUNK_SIZE = 8 * 1024;

    /**
     * Set HTTP initial line length to 4k.
     * See {@link io.netty.handler.codec.http.HttpClientCodec}.
     */
    int DEFAULT_MAX_INITIAL_LINE_LENGTH = 4 * 1024;

    /**
     * Set HTTP maximum headers size to 8k.
     * See  {@link io.netty.handler.codec.http.HttpClientCodec}.
     */
    int DEFAULT_MAX_HEADERS_SIZE = 8 * 1024;

    /**
     * Set maximum content length to 100 MB.
     */
    int DEFAULT_MAX_CONTENT_LENGTH = 100 * 1024 * 1024;

    /**
     * This is Netty's default.
     * See {@link io.netty.handler.codec.MessageAggregator#DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    int DEFAULT_MAX_COMPOSITE_BUFFER_COMPONENTS = 1024;

    /**
     * Allow maximum concurrent connections to an {@link InetAddressKey}.
     * Usually, browsers restrict concurrent connections to 8 for a single address.
     */
    int DEFAULT_MAX_CONNECTIONS = 8;

    /**
     * Default read/write timeout in milliseconds.
     */
    int DEFAULT_TIMEOUT_MILLIS = 5000;

    /**
     * Default for gzip codec.
     */
    boolean DEFAULT_ENABLE_GZIP = true;

    /**
     * Default for HTTP/2 only.
     */
    boolean DEFAULT_INSTALL_HTTP_UPGRADE2 = false;

    /**
     * Default SSL provider.
     */
    SslProvider DEFAULT_SSL_PROVIDER = SslProvider.OPENSSL;

    Iterable<String> DEFAULT_CIPHERS = Http2SecurityUtil.CIPHERS;

    CipherSuiteFilter DEFAULT_CIPHER_SUITE_FILTER = SupportedCipherSuiteFilter.INSTANCE;

    TrustManagerFactory DEFAULT_TRUST_MANAGER_FACTORY = InsecureTrustManagerFactory.INSTANCE;

    boolean DEFAULT_USE_SERVER_NAME_IDENTIFICATION = true;

    /**
     * Default for SSL client authentication.
     */
    SslClientAuthMode DEFAULT_SSL_CLIENT_AUTH_MODE = SslClientAuthMode.NONE;
}
