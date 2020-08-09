package org.xbib.netty.http.common.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Abstract multi map.
 *
 * @param <K> the key type parameter
 * @param <V> the value type parameter
 */
abstract class AbstractMultiMap<K, V> implements MultiMap<K, V> {

    private final Map<K, Collection<V>> map;

    AbstractMultiMap() {
        this(null);
    }

    private AbstractMultiMap(MultiMap<K, V> map) {
        this.map = newMap();
        if (map != null) {
            putAll(map);
        }
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    @Override
    public Set<K> keySet() {
        return map.keySet();
    }

    @Override
    public boolean put(K key, V value) {
        Collection<V> set = map.get(key);
        if (set == null) {
            set = newValues();
            set.add(value);
            map.put(key, set);
            return true;
        } else {
            set.add(value);
            return false;
        }
    }

    @Override
    public void putAll(K key, Iterable<V> values) {
        if (values == null) {
            return;
        }
        Collection<V> set = map.computeIfAbsent(key, k -> newValues());
        for (V v : values) {
            set.add(v);
        }
    }

    @Override
    public Collection<V> get(K key) {
        return map.get(key);
    }

    @Override
    public Collection<V> remove(K key) {
        return map.remove(key);
    }

    @Override
    public boolean remove(K key, V value) {
        Collection<V> set = map.get(key);
        return set != null && set.remove(value);
    }

    @Override
    public void putAll(MultiMap<K, V> map) {
        if (map != null) {
            for (K key : map.keySet()) {
                putAll(key, map.get(key));
            }
        }
    }

    @Override
    public Map<K, Collection<V>> asMap() {
        return map;
    }

    @Override
    public String getString(K key) {
        Collection<V> v = get(key);
        return v != null ? v.iterator().next().toString() : null;
    }

    @Override
    public String getString(K key, String defaultValue) {
        Collection<V> collection = get(key);
        Iterator<V> iterator = collection != null ? collection.iterator() : null;
        V v = iterator != null ?  iterator.next() : null;
        return  v != null ? v.toString() : defaultValue;
    }

    @Override
    public Integer getInteger(K key, int defaultValue) {
        Collection<V> collection = get(key);
        Iterator<V> iterator = collection != null ? collection.iterator() : null;
        V v = iterator != null ?  iterator.next() : null;
        return v != null ? Integer.parseInt(v.toString()) : defaultValue;
    }

    @Override
    public Boolean getBoolean(K key, boolean defaultValue) {
        Collection<V> collection = get(key);
        Iterator<V> iterator = collection != null ? collection.iterator() : null;
        V v = iterator != null ?  iterator.next() : null;
        return v != null ? Boolean.parseBoolean(v.toString()) : defaultValue;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof AbstractMultiMap && map.equals(((AbstractMultiMap) obj).map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }

    @Override
    public String toString() {
        return map.toString();
    }

    abstract Collection<V> newValues();

    abstract Map<K, Collection<V>> newMap();
}
