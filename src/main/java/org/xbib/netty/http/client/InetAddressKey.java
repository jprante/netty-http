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

import io.netty.handler.codec.http.HttpVersion;

import java.net.InetSocketAddress;
import java.net.URL;

/**
 */
public class InetAddressKey {

    private final InetSocketAddress inetSocketAddress;

    private final HttpVersion version;

    private final Boolean secure;

    InetAddressKey(URL url, HttpVersion version) {
        this.version = version;
        String protocol = url.getProtocol();
        this.secure = "https".equals(protocol);
        int port = url.getPort();
        if (port == -1) {
            port = "http".equals(protocol) ? 80 : (secure ? 443 : -1);
        }
        this.inetSocketAddress = new InetSocketAddress(url.getHost(), port);
    }

    InetAddressKey(InetSocketAddress inetSocketAddress, HttpVersion version, boolean secure) {
        this.inetSocketAddress = inetSocketAddress;
        this.version = version;
        this.secure = secure;
    }

    InetSocketAddress getInetSocketAddress() {
        return inetSocketAddress;
    }

    HttpVersion getVersion() {
        return version;
    }

    boolean isSecure() {
        return secure;
    }

    public String toString() {
        return inetSocketAddress + " (version:" + version + ",secure:" + secure + ")";
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof InetAddressKey &&
                inetSocketAddress.equals(((InetAddressKey) object).inetSocketAddress) &&
                version.equals(((InetAddressKey) object).version) &&
                secure == ((InetAddressKey) object).secure;
    }

    @Override
    public int hashCode() {
        return inetSocketAddress.hashCode() ^ version.hashCode() ^ secure.hashCode();
    }
}
