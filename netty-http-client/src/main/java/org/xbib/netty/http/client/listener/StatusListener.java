package org.xbib.netty.http.client.listener;


import org.xbib.netty.http.common.HttpStatus;

@FunctionalInterface
public interface StatusListener {

    void onStatus(HttpStatus httpStatus);
}
