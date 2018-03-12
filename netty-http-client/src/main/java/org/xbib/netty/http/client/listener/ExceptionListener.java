package org.xbib.netty.http.client.listener;

@FunctionalInterface
public interface ExceptionListener {

    void onException(Throwable throwable);
}
