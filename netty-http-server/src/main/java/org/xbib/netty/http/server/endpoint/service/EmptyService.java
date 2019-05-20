package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.service.Service;

import java.io.IOException;

public class EmptyService implements Service {
    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        // do nothing
    }
}
