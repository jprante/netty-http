package org.xbib.netty.http.server.context;

import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;

import java.io.IOException;

/**
 * A {@code ContextHandler} is capable of serving content for resources within its context.
 *
 * @see VirtualServer#addContext
 */
@FunctionalInterface
public interface ContextHandler {

    /**
     * Serves the given request using the given response.
     *
     * @param serverRequest  the request to be served
     * @param serverResponse the response to be filled
     * @throws IOException if an IO error occurs
     */
    void serve(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
