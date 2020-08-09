package org.xbib.netty.http.common.util;

import java.util.List;
import java.util.Map;

public interface ParameterMap extends Iterable<Map.Entry<String, String>> {

    String get(String name);

    List<String> getAll(String name);

    List<Map.Entry<String, String>> entries();

    boolean contains(String name);

    default boolean contains(String name, String value, boolean caseInsensitive) {
        return getAll(name).stream()
                .anyMatch(val -> caseInsensitive ? val.equalsIgnoreCase(value) : val.equals(value));
    }

    boolean isEmpty();

    List<String> names();

    ParameterMap add(String name, String value);

    ParameterMap add(String name, Iterable<String> values);

    ParameterMap addAll(ParameterMap map);

    ParameterMap addAll(Map<String, String> map);

    ParameterMap set(String name, String value);

    ParameterMap set(String name, Iterable<String> values);

    ParameterMap setAll(ParameterMap map);

    ParameterMap remove(String name);

    ParameterMap clear();

    int size();
}
