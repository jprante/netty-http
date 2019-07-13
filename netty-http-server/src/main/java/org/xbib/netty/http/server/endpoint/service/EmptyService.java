package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

public class EmptyService implements Service {
    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) {
        // do nothing
    }
}
