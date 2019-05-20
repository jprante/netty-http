package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;

@FunctionalInterface
public interface EndpointDispatcher {

    void dispatch(Endpoint endpoint, ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
