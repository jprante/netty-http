package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.server.ServerRequest;

import java.io.IOException;

public interface Endpoint {

    String getPrefix();

    String getPath();

    boolean matches(EndpointInfo info);

    void resolveUriTemplate(ServerRequest serverRequest) throws IOException;
}
