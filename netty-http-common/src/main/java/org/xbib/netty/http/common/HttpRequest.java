package org.xbib.netty.http.common;

import java.io.InputStream;
import java.util.Map;

/**
 * A representation of an HTTP request. Contains methods to access all those parts of an HTTP request.
 */
public interface HttpRequest {

    String getMethod();

    String getRequestUrl();

    void setRequestUrl(String url);

    void setHeader(String name, String value);

    String getHeader(String name);

    Map<String, String> getHeaders();

    InputStream getContent();

    String getContentType();

}
