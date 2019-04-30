package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.server.transport.ServerRequest;
import org.xbib.netty.http.server.transport.ServerResponse;

import java.io.IOException;

/**
 * A {@code Handler} is capable of serving content for resources within its context.
 *
 * @see NamedServer#addHandler
 */
@FunctionalInterface
public interface Handler {

    /**
     * Handles the given request by using the given response.
     *
     * @param serverRequest the request to be served
     * @param serverResponse the response to be generated
     * @throws IOException if an IO error occurs
     */
    void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
