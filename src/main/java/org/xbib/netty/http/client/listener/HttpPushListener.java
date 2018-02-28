package org.xbib.netty.http.client.listener;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2Headers;

@FunctionalInterface
public interface HttpPushListener {

    void onPushReceived(Http2Headers headers, FullHttpResponse fullHttpResponse);
}
