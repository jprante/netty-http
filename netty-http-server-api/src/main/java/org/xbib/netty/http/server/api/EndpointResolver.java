package org.xbib.netty.http.server.api;

import org.xbib.netty.http.common.HttpMethod;
import java.io.IOException;
import java.util.List;

public interface EndpointResolver<E extends Endpoint<?>> {

    List<E> matchingEndpointsFor(String path, HttpMethod method, String contentType);

    void handle(E matchingEndpoint,
                ServerRequest serverRequest,
                ServerResponse serverResponse) throws IOException;
}
