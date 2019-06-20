package org.xbib.netty.http.server.endpoint.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.io.IOException;
import java.net.URL;

public abstract class URLService extends ResourceService {

    @Override
    protected void handleResource(String resourcePath, ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        URL url = getResourceURL(resourcePath);
        if (url != null) {
            streamResource(url, serverRequest, serverResponse);
        }
    }

    protected abstract URL getResourceURL(String resourcePath);

    protected void streamResource(URL resourceUrl, ServerRequest serverRequest,
                                  ServerResponse serverResponse) throws IOException {
        /*long lastModified = resourceUrl.openConnection().getLastModified();
        serverResponse.addEtag(serverRequest, lastModified);
        if (serverResponse.getLastStatus() == HttpResponseStatus.NOT_MODIFIED) {
            ServerResponse.write(serverResponse, HttpResponseStatus.NOT_MODIFIED);
        } else {
            sendResource(resourceUrl, serverRequest, serverResponse);
        }*/
    }
}
