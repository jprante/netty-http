package org.xbib.netty.http.client.api;

import java.io.Closeable;

public interface Pool<T> extends Closeable {

    void prepare(int count) throws Exception;

    T acquire() throws Exception;

    void release(T t, boolean close) throws Exception;

    enum PoolKeySelectorType {
        RANDOM, ROUNDROBIN
    }
}
