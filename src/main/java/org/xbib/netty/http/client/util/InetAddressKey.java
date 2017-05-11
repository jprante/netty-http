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
package org.xbib.netty.http.client.util;

import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;

/**
 * A key for host, port, HTTP version, and secure transport mode of a channel for HTTP.
 */
public class InetAddressKey {

    private final String host;

    private final int port;

    private final HttpVersion version;

    private final Boolean secure;

    private InetSocketAddress inetSocketAddress;

    public InetAddressKey(String host, int port, HttpVersion version, boolean secure) {
        this.host = host;
        this.port = port == -1 ? secure ? 443 : 80 : port;
        this.version = version;
        this.secure = secure;
    }

    public InetSocketAddress getInetSocketAddress() {
        if (inetSocketAddress == null) {
            this.inetSocketAddress = new InetSocketAddress(host, port);
        }
        return inetSocketAddress;
    }

    public HttpVersion getVersion() {
        return version;
    }

    public boolean isSecure() {
        return secure;
    }

    public String toString() {
        return host + ":" + port + " (version:" + version + ",secure:" + secure + ")";
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof InetAddressKey &&
                host.equals(((InetAddressKey) object).host) &&
                port == ((InetAddressKey) object).port &&
                version.equals(((InetAddressKey) object).version) &&
                secure.equals(((InetAddressKey) object).secure);
    }

    @Override
    public int hashCode() {
        return host.hashCode() ^ port ^ version.hashCode() ^ secure.hashCode();
    }
}
