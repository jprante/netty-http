package org.xbib.netty.http.server.api;

import java.io.IOException;
import java.util.List;

public interface EndpointResolver<E extends Endpoint<? extends EndpointDescriptor>> {

    List<E> matchingEndpointsFor(ServerRequest serverRequest);

    void resolve(List<E> matchingEndpoints,
                 ServerRequest serverRequest) throws IOException;

    void handle(List<E> matchingEndpoints,
                ServerRequest serverRequest,
                ServerResponse serverResponse) throws IOException;
}
