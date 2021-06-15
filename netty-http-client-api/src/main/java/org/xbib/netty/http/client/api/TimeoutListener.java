package org.xbib.netty.http.client.api;

@FunctionalInterface
public interface TimeoutListener {

    void onTimeout(Request request);
}
