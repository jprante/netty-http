package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;

/**
 * A {@code Service} is capable of serving requests for resources within its context.
 */
@FunctionalInterface
public interface Service {

    /**
     * Handles the given request by building and returning a response.
     *
     * @param serverRequest the request to be served
     * @param serverResponse the response to be written
     * @throws IOException if an IO error occurs
     */
    void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
