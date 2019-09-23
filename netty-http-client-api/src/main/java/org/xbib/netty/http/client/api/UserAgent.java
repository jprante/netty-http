package org.xbib.netty.http.client.api;

import io.netty.bootstrap.Bootstrap;

import java.util.Optional;

/**
 * HTTP client user agent.
 */
public final class UserAgent {

    /**
     * The default value for {@code User-Agent}.
     */
    private static final String USER_AGENT = String.format("NettyHttpClient/%s (Java/%s/%s) (Netty/%s)",
            httpClientVersion(), javaVendor(), javaVersion(), nettyVersion());

    private UserAgent() {
    }

    public static String getUserAgent() {
        return USER_AGENT;
    }

    private static String httpClientVersion() {
        return Optional.ofNullable(UserAgent.class.getPackage().getImplementationVersion())
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
