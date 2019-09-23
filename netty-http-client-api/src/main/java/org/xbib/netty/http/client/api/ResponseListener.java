package org.xbib.netty.http.client.api;

import org.xbib.netty.http.common.HttpResponse;

@FunctionalInterface
public interface ResponseListener<R extends HttpResponse> {

    void onResponse(R response);
}
