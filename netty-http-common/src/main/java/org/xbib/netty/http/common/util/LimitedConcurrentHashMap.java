package org.xbib.netty.http.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("serial")
public class LimitedConcurrentHashMap<K, V> extends ConcurrentHashMap<K, V> {

    private final Semaphore semaphore;

    public LimitedConcurrentHashMap(int limit) {
        super(16, 0.75f);
        this.semaphore = new Semaphore(limit);
    }

    @Override
    public V put(K key, V value) {
        try {
            if (semaphore.tryAcquire(1L, TimeUnit.SECONDS)) {
                return super.put(key, value);
            }
        } catch (InterruptedException e) {
            throw new IllegalArgumentException("size limit exceeded");
        }
        throw new IllegalArgumentException("size limit exceeded");
    }

    @Override
    public V remove(Object key) {
        V v;
        try {
            v = super.remove(key);
        } finally {
            semaphore.release();
        }
        return v;
    }
}
