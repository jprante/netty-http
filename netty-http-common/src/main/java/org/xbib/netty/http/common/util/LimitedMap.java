package org.xbib.netty.http.common.util;

import java.util.SortedSet;
import java.util.TreeMap;

@SuppressWarnings("serial")
public class LimitedMap<K, V> extends TreeMap<K, SortedSet<V>> {

    private final int limit;

    public LimitedMap(int limit) {
        this.limit = limit;
    }

    @Override
    public SortedSet<V> put(K key, SortedSet<V> value) {
        if (size() < limit) {
            return super.put(key, value);
        }
        return null;
    }
}
