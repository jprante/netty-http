package org.xbib.netty.http.client.listener;

import org.xbib.netty.http.common.HttpResponse;

@FunctionalInterface
public interface ResponseListener<R extends HttpResponse> {

    void onResponse(R response);
}
