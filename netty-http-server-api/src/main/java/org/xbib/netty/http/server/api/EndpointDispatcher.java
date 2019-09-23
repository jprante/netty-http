package org.xbib.netty.http.server.api;

import java.io.IOException;

@FunctionalInterface
public interface EndpointDispatcher<E extends Endpoint<?>> {

    void dispatch(E endpoint, ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
