package org.xbib.netty.http.common.util;

import java.util.LinkedHashMap;

@SuppressWarnings("serial")
public class LimitedMap<K, V> extends LinkedHashMap<K, V> {

    private final int limit;

    public LimitedMap(int limit) {
        super(16, 0.75f, true);
        this.limit = limit;
    }

    @Override
    public V put(K key, V value) {
        if (size() < limit) {
            return super.put(key, value);
        }
        throw new IllegalArgumentException("size limit exceeded: " + limit);
    }
}
