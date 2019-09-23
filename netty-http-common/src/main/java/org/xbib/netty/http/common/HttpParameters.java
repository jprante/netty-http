package org.xbib.netty.http.common;

import io.netty.handler.codec.http.HttpHeaderValues;
import org.xbib.net.PercentDecoder;
import org.xbib.net.PercentEncoder;
import org.xbib.net.PercentEncoders;
import org.xbib.netty.http.common.util.LimitedSet;
import org.xbib.netty.http.common.util.LimitedTreeMap;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * A limited multi-map of HTTP request parameters. Each key references a
 * limited set of parameters collected from the request during message
 * signing. Parameter values are sorted as per
 * <a href="http://oauth.net/core/1.0a/#anchor13">OAuth specification</a>.
 * Every key/value pair will be percent-encoded upon insertion.
 *  This class has special semantics tailored to
 * being useful for message signing; it's not a general purpose collection class
 * to handle request parameters.
 */
public class HttpParameters implements Map<String, SortedSet<String>> {

    private static final String EQUALS = "=";

    private static final String AMPERSAND = "&";

    private final int maxParam;

    private final int sizeLimit;

    private final int elementSizeLimit;

    private final LimitedTreeMap<String, String> map;

    private final PercentEncoder percentEncoder;

    private final PercentDecoder percentDecoder;

    private final CharSequence contentType;

    private final Charset charset;

    public HttpParameters() {
        this(1024, 1024, 65536,
                HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.UTF_8);
    }

    public HttpParameters(String contentType) {
        this(1024, 1024, 65536,
                contentType, StandardCharsets.UTF_8);
    }

    public HttpParameters(int maxParam, int sizeLimit, int elementSizeLimit,
                          CharSequence contentType, Charset charset) {
        this.maxParam = maxParam;
        this.sizeLimit = sizeLimit;
        this.elementSizeLimit = elementSizeLimit;
        this.map = new LimitedTreeMap<>(maxParam);
        this.percentEncoder = PercentEncoders.getQueryEncoder(charset);
        this.percentDecoder = new PercentDecoder();
        this.contentType = contentType;
        this.charset = charset;
    }

    @Override
    public SortedSet<String> put(String key, SortedSet<String> value) {
        return map.put(key, value);
    }

    @Override
    public SortedSet<String> get(Object key) {
        return map.get(key);
    }

    @Override
    public void putAll(Map<? extends String, ? extends SortedSet<String>> m) {
        map.putAll(m);
    }

    @Override
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        if (value instanceof String) {
            for (Set<String> values : map.values()) {
                if (values.contains(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int size() {
        int count = 0;
        for (String key : map.keySet()) {
            count += map.get(key).size();
        }
        return count;
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public SortedSet<String> remove(Object key) {
        return map.remove(key);
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<SortedSet<String>> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, SortedSet<String>>> entrySet() {
        return map.entrySet();
    }

    public SortedSet<String> put(String key, SortedSet<String> values, boolean percentEncode) {
        if (percentEncode) {
            remove(key);
            for (String v : values) {
                add(key, v, true);
            }
            return get(key);
        } else {
            return map.put(key, values);
        }
    }

    /**
     * Convenience method to add a single value for the parameter specified by 'key'.
     *
     * @param key the parameter name
     * @param value the parameter value
     * @return the value
     */
    public String add(String key, String value) {
        return add(key, value, false);
    }

    /**
     * Convenience method to add a single value for the parameter specified by
     * 'key'.
     *
     * @param key the parameter name
     * @param value the parameter value
     * @param percentEncode whether key and value should be percent encoded before being
     *        inserted into the map
     * @return the value
     */
    public String add(String key, String value, boolean percentEncode) {
        String v = null;
        try {
            String k = percentEncode ? percentEncoder.encode(key) : key;
            SortedSet<String> values = map.get(k);
            if (values == null) {
                values = new LimitedSet<>(sizeLimit, elementSizeLimit);
                map.put(k, values);
            }

            if (value != null) {
                v = percentEncode ? percentEncoder.encode(value) : value;
                values.add(v);
            }
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        }
        return v;
    }

    /**
     * Convenience method to allow for storing null values. {@link #put} doesn't
     * allow null values, because that would be ambiguous.
     *
     * @param key the parameter name
     * @param nullString can be anything, but probably... null?
     * @return null
     */
    public String addNull(String key, String nullString) {
        return add(key, nullString);
    }

    public void addAll(Map<? extends String, ? extends SortedSet<String>> m, boolean percentEncode)
            throws MalformedInputException, UnmappableCharacterException {
        if (percentEncode) {
            for (String key : m.keySet()) {
                put(key, m.get(key), true);
            }
        } else {
            map.putAll(m);
        }
    }

    public void addAll(String[] keyValuePairs, boolean percentEncode) {
        for (int i = 0; i < keyValuePairs.length - 1; i += 2) {
            add(keyValuePairs[i], keyValuePairs[i + 1], percentEncode);
        }
    }

    /**
     * Convenience method to merge a {@code Map<String, List<String>>}.
     *
     * @param m the map
     */
    public void addMap(Map<String, List<String>> m) {
        for (String key : m.keySet()) {
            SortedSet<String> vals = get(key);
            if (vals == null) {
                vals = new LimitedSet<>(sizeLimit, elementSizeLimit);
                put(key, vals);
            }
            vals.addAll(m.get(key));
        }
    }

    public String getFirst(String key) {
        SortedSet<String> values = map.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.first();
    }

    /**
     * Returns the first value from the set of all values for the given
     * parameter name. If the key passed to this method contains special
     * characters, you must first percent encode it, otherwise the lookup will fail
     * (that's because upon storing values in this map, keys get
     * percent-encoded).
     *
     * @param key the parameter name (must be percent encoded if it contains unsafe
     *        characters!)
     * @return the first value found for this parameter
     * @throws MalformedInputException if input is malformed
     * @throws UnmappableCharacterException if characters are unmappable
     */
    public String getFirstDecoded(String key)
            throws MalformedInputException, UnmappableCharacterException {
        SortedSet<String> values = map.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String value = values.first();
        return percentDecoder.decode(value);
    }

    public CharSequence getContentType() {
        return contentType;
    }

    public String getAsQueryString(boolean percentEncode) throws MalformedInputException, UnmappableCharacterException {
        List<String> list = new ArrayList<>();
        for (String key : keySet()) {
            list.add(getAsQueryString(key, percentEncode));
        }
        return String.join(AMPERSAND, list);
    }

    /**
     * Concatenates all values for the given key to a list of key/value pairs
     * suitable for use in a URL query string.
     *
     * @param key the parameter name
     * @return the query string
     * @throws MalformedInputException if input is malformed
     * @throws UnmappableCharacterException if characters are unmappable
     */
    public String getAsQueryString(String key)
            throws MalformedInputException, UnmappableCharacterException {
        return getAsQueryString(key, true);
    }

    /**
     * Concatenates all values for the given key to a list of key/value pairs
     * suitable for use in a URL query string.
     *
     * @param key the parameter name
     * @param percentEncode whether key should be percent encoded before being
     *        used with the map
     * @return the query string
     * @throws MalformedInputException if input is malformed
     * @throws UnmappableCharacterException if characters are unmappable
     */
    public String getAsQueryString(String key, boolean percentEncode)
            throws MalformedInputException, UnmappableCharacterException {
        String k = percentEncode ? percentEncoder.encode(key) : key;
        SortedSet<String> values = map.get(k);
        if (values == null) {
            return k + EQUALS;
        }
        Iterator<String> it = values.iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            String v = it.next();
            v = percentEncode ? percentEncoder.encode(v) : v;
            sb.append(k).append(EQUALS).append(v);
            if (it.hasNext()) {
                sb.append(AMPERSAND);
            }
        }
        return sb.toString();
    }

    public String getAsHeaderElement(String key) {
        String value = getFirst(key);
        if (value == null) {
            return null;
        }
        return key + "=\"" + value + "\"";
    }

    public HttpParameters getOAuthParameters() {
        HttpParameters oauthParams =
                new HttpParameters(maxParam, sizeLimit, elementSizeLimit, contentType, StandardCharsets.UTF_8);
        entrySet().stream().filter(entry -> entry.getKey().startsWith("oauth_") || entry.getKey().startsWith("x_oauth_"))
                .forEach(entry -> oauthParams.put(entry.getKey(), entry.getValue()));
        return oauthParams;
    }

    @Override
    public String toString() {
        return new LinkedHashMap<>(this).toString();
    }

}
