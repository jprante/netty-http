package org.xbib.netty.http.client.listener;

import io.netty.handler.codec.http.HttpResponseStatus;

@FunctionalInterface
public interface StatusListener {

    void onStatus(HttpResponseStatus httpResponseStatus);
}
