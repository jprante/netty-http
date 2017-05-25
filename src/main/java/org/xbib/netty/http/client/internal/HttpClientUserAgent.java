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
package org.xbib.netty.http.client.internal;

import io.netty.bootstrap.Bootstrap;
import org.xbib.netty.http.client.HttpClient;

import java.util.Optional;

/**
 */
public final class HttpClientUserAgent {

    /**
     * The default valut for {@code User-Agent}.
     */
    private static final String USER_AGENT = String.format("XbibHttpClient/%s (Java/%s/%s) (Netty/%s)",
            httpClientVersion(), javaVendor(), javaVersion(), nettyVersion());

    private HttpClientUserAgent() {
    }

    public static String getUserAgent() {
        return USER_AGENT;
    }

    private static String httpClientVersion() {
        return Optional.ofNullable(HttpClient.class.getPackage().getImplementationVersion())
                .orElse("unknown");
    }

    private static String javaVendor() {
        return Optional.ofNullable(System.getProperty("java.vendor"))
                .orElse("unknown");
    }

    private static String javaVersion() {
        return Optional.ofNullable(System.getProperty("java.version"))
                .orElse("unknown");
    }

    private static String nettyVersion() {
        return Optional.ofNullable(Bootstrap.class.getPackage().getImplementationVersion())
                .orElse("unknown");
    }
}
