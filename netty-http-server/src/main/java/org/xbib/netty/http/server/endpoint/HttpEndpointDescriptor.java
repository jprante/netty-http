package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.server.transport.HttpServerRequest;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;

public class HttpEndpointDescriptor implements EndpointDescriptor, Comparable<HttpEndpointDescriptor> {

    private final String path;

    private final String method;

    private final String contentType;

    public HttpEndpointDescriptor(HttpServerRequest serverRequest) {
        this.path = extractPath(serverRequest.getRequest().uri());
        this.method = serverRequest.getRequest().method().name();
        this.contentType = serverRequest.getRequest().headers().get(CONTENT_TYPE);
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public String getContentType() {
        return contentType;
    }

    @Override
    public String toString() {
        return "[HttpEndpointDescriptor:path=" + path + ",method=" + method + ",contentType=" + contentType + "]";
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HttpEndpointDescriptor && toString().equals(o.toString());
    }

    @Override
    public int compareTo(HttpEndpointDescriptor o) {
        return toString().compareTo(o.toString());
    }

    private static String extractPath(String uri) {
        String path = uri;
        int pos = uri.lastIndexOf('#');
        path = pos >= 0 ? path.substring(0, pos) : path;
        pos = uri.lastIndexOf('?');
        path = pos >= 0 ? path.substring(0, pos) : path;
        return path;
    }
}
