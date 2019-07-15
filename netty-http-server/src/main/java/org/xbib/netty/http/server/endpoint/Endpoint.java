package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;

public interface Endpoint<D extends EndpointDescriptor> {

    String getPrefix();

    String getPath();

    boolean matches(D descriptor);

    void resolveUriTemplate(ServerRequest serverRequest) throws IOException;

    void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
