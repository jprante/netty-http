package org.xbib.netty.http.client.listener;

import io.netty.handler.codec.http.FullHttpResponse;

@FunctionalInterface
public interface HttpResponseListener {

    void onResponse(FullHttpResponse fullHttpResponse);
}
