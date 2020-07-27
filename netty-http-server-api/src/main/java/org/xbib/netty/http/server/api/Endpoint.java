package org.xbib.netty.http.server.api;

import java.io.IOException;

public interface Endpoint<D extends EndpointDescriptor> {

    String getPrefix();

    String getPath();

    boolean matches(D descriptor);

    ServerRequest resolveRequest(ServerRequest.Builder serverRequestBuilder,
                                 Domain<? extends EndpointResolver<? extends Endpoint<?>>> domain,
                                 EndpointResolver<? extends Endpoint<?>> endpointResolver);

    void before(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;

    void after(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
