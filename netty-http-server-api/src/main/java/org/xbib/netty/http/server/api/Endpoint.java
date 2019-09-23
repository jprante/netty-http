package org.xbib.netty.http.server.api;

import java.io.IOException;

public interface Endpoint<D extends EndpointDescriptor> {

    String getPrefix();

    String getPath();

    boolean matches(D descriptor);

    void resolveUriTemplate(ServerRequest serverRequest) throws IOException;

    void before(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;

    void after(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException;
}
