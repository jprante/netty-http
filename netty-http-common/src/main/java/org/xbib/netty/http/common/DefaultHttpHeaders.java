package org.xbib.netty.http.common;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DefaultHttpHeaders implements HttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders httpHeaders;

    public DefaultHttpHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        this.httpHeaders = headers;
    }

    @Override
    public String getHeader(CharSequence header) {
        return httpHeaders.get(header);
    }

    @Override
    public List<String> getAllHeaders(CharSequence header) {
        return httpHeaders.getAll(header);
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return httpHeaders.iteratorCharSequence();
    }

    @Override
    public String toString() {
        return httpHeaders.entries().toString();
    }
}
