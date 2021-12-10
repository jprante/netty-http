package org.xbib.netty.http.server.handler;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.Mapping;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.api.ServerConfig;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

public class ExtendedSNIHandler extends SniHandler {

    private static final Logger logger = Logger.getLogger(ExtendedSNIHandler.class.getName());

    private final ServerConfig serverConfig;

    private final HttpAddress httpAddress;

    public ExtendedSNIHandler(Mapping<? super String, ? extends SslContext> mapping,
                              ServerConfig serverConfig, HttpAddress httpAddress) {
        super(mapping);
        this.serverConfig = serverConfig;
        this.httpAddress = httpAddress;
    }

    @Override
    protected SslHandler newSslHandler(SslContext context, ByteBufAllocator allocator) {
        return newSslHandler(context, serverConfig, allocator, httpAddress);
    }

    private static SslHandler newSslHandler(SslContext sslContext,
                                            ServerConfig serverConfig,
                                            ByteBufAllocator allocator,
                                            HttpAddress httpAddress) {
        InetSocketAddress peer = httpAddress.getInetSocketAddress();
        SslHandler sslHandler = sslContext.newHandler(allocator, peer.getHostName(), peer.getPort());
        SSLEngine engine = sslHandler.engine();
        SSLParameters params = engine.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        engine.setSSLParameters(params);
        logger.log(Level.FINE, () -> "set enabled TLS protocols in SSL engine: " + Arrays.asList(serverConfig.getProtocols()));
        engine.setEnabledProtocols(serverConfig.getProtocols());
        return sslHandler;
    }
}
