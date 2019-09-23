package org.xbib.netty.http.server.api;

import java.io.IOException;

/**
 * A {@code Filter} is capable of serving requests for resources within its context.
 */
@FunctionalInterface
public interface Filter {

    /**
     * Handles the given request by building and returning a response.
     *
     * @param serverRequest the request to be served
     * @param serverResponse the response to be written
     * @throws IOException if an IO error occurs
     */
    void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
