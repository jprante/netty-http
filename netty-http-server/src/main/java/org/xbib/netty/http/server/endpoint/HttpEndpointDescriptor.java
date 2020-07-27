package org.xbib.netty.http.server.endpoint;

import org.xbib.netty.http.common.HttpMethod;
import org.xbib.netty.http.server.api.EndpointDescriptor;

public class HttpEndpointDescriptor implements EndpointDescriptor, Comparable<HttpEndpointDescriptor> {

    private final String path;

    private final HttpMethod method;

    private final String contentType;

    public HttpEndpointDescriptor(String path, HttpMethod method, String contentType) {
        this.path = path;
        this.method = method;
        this.contentType = contentType;
    }

    @Override
    public String getSortKey() {
        return path;
    }

    public String getPath() {
        return path;
    }

    public HttpMethod getMethod() {
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
}
