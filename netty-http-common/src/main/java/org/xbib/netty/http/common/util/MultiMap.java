package org.xbib.netty.http.common.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * MultiMap interface.
 *
 * @param <K> the key type parameter
 * @param <V> the value type parameter
 */
public interface MultiMap<K, V> {

    void clear();

    int size();

    boolean isEmpty();

    boolean containsKey(K key);

    Collection<V> get(K key);

    Set<K> keySet();

    boolean put(K key, V value);

    void putAll(K key, Iterable<V> values);

    void putAll(MultiMap<K, V> map);

    Collection<V> remove(K key);

    boolean remove(K key, V value);

    Map<K, Collection<V>> asMap();

    String getString(K key);

    String getString(K key, String defaultValue);

    Integer getInteger(K key, int defaultValue);

    Boolean getBoolean(K key, boolean defaultValue);
}
