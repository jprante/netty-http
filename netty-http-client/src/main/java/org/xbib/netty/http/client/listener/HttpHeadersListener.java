package org.xbib.netty.http.client.listener;

import io.netty.handler.codec.http.HttpHeaders;

@FunctionalInterface
public interface HttpHeadersListener {

    void onHeaders(HttpHeaders httpHeaders);
}
