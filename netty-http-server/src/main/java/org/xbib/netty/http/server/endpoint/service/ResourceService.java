package org.xbib.netty.http.server.endpoint.service;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;

public abstract class ResourceService implements Service {

    @Override
    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        String resourcePath = getResourcePath(serverRequest);
        handleResource(resourcePath, serverRequest, serverResponse);
    }

    protected abstract void handleResource(String resourcePath, ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;

    protected String getResourcePath(ServerRequest serverRequest) {
        return serverRequest.getEffectiveRequestPath().substring(1);
    }
}
