package org.xbib.netty.http.client.pool;

import java.io.Closeable;
import java.util.List;

public interface Pool<T> extends Closeable {

    void prepare(int count) throws Exception;

    T acquire() throws Exception;

    int acquire(List<T> list, int maxCount) throws Exception;

    void release(T t) throws Exception;

    void release(List<T> list) throws Exception;
}
