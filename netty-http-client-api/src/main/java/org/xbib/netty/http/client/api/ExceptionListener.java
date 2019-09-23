package org.xbib.netty.http.client.api;

@FunctionalInterface
public interface ExceptionListener {

    void onException(Throwable throwable);
}
