package org.xbib.netty.http.client.api;

import io.netty.handler.codec.http.websocketx.WebSocketFrame;

@FunctionalInterface
public interface WebSocketResponseListener<F extends WebSocketFrame> {

    void onResponse(F frame);
}
