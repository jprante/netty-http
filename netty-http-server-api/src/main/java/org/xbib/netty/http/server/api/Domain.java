package org.xbib.netty.http.server.api;

import io.netty.handler.ssl.SslContext;
import org.xbib.netty.http.common.HttpAddress;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collection;

public interface Domain<R extends EndpointResolver<?>> {

    HttpAddress getHttpAddress();

    String getName();

    Collection<R> getHttpEndpointResolvers();

    SslContext getSslContext();

    Collection<? extends X509Certificate> getCertificateChain();

    void handle(ServerRequest.Builder serverRequestBuilder, ServerResponse.Builder serverResponseBuilder) throws IOException;

    void handleAfterError(ServerRequest.Builder serverRequestBuilder, ServerResponse.Builder serverResponseBuilder, Throwable throwable);
}
