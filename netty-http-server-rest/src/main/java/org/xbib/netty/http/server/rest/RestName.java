package org.xbib.netty.http.server.rest;

import io.netty.bootstrap.Bootstrap;

import java.util.Optional;

/**
 * Server name.
 */
public final class RestName {

    /**
     * The default value for {@code Server} header.
     */
    private static final String SERVER_NAME = String.format("RestServer/%s (Java/%s/%s) (Netty/%s)",
            httpServerVersion(), javaVendor(), javaVersion(), nettyVersion());

    private RestName() {
    }

    public static String getServerName() {
        return SERVER_NAME;
    }

    private static String httpServerVersion() {
        return Optional.ofNullable(Rest.class.getPackage().getImplementationVersion())
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
