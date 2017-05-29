package org.xbib.netty.http.client;

import org.xbib.netty.http.client.util.AbstractFuture;

/**
 * A HTTP request future.
 *
 * @param <V> the response type parameter.
 */
public class HttpRequestFuture<V> extends AbstractFuture<V> {

    public void success(V v) {
        set(v);
    }

    public void fail(Exception e) {
        setException(e);
    }

}
