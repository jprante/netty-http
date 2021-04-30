package org.xbib.netty.http.common;

import io.netty.handler.codec.http.HttpHeaderValues;
import org.xbib.net.PercentEncoder;
import org.xbib.net.PercentEncoders;
import org.xbib.netty.http.common.util.CaseInsensitiveParameters;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

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
public class HttpParameters extends CaseInsensitiveParameters {

    private static final String EQUALS = "=";

    private static final String AMPERSAND = "&";

    private final int sizeLimit;

    private final int elementSizeLimit;

    private final PercentEncoder percentEncoder;

    private final CharSequence contentType;

    private final Charset encoding;

    public HttpParameters() {
        this(1024, 1024, 65536,
                HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.UTF_8);
    }

    public HttpParameters(CharSequence contentType) {
        this(1024, 1024, 65536,
                contentType, StandardCharsets.UTF_8);
    }

    public HttpParameters(CharSequence contentType, Charset charset) {
        this(1024, 1024, 65536,
                contentType, charset);
    }

    public HttpParameters(int maxParam, int sizeLimit, int elementSizeLimit,
                          CharSequence contentType, Charset charset) {
        this.sizeLimit = sizeLimit;
        this.elementSizeLimit = elementSizeLimit;
        this.percentEncoder = PercentEncoders.getQueryEncoder(charset);
        this.contentType = contentType;
        this.encoding = charset;
    }

    public CharSequence getContentType() {
        return contentType;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public Collection<String> put(String key, Collection<String> values, boolean percentEncode) {
        remove(key);
        for (String v : values) {
            add(key, v, percentEncode);
        }
        return getAll(key);
    }

    /**
     * Convenience method to add a single value for the parameter specified by 'key'.
     *
     * @param key the parameter name
     * @param value the parameter value
     * @return the value
     */
    public HttpParameters addRaw(String key, String value) {
        return add(key, value, false);
    }

    public HttpParameters add(String key, String value) {
        return add(key, value, true);
    }

    /**
     * Convenience method to allow for storing null values. {@link #put} doesn't
     * allow null values, because that would be ambiguous.
     *
     * @param key the parameter name
     * @param nullString can be anything, but probably... null?
     * @return null
     */
    public HttpParameters addNull(String key, String nullString) {
        return addRaw(key, nullString);
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
    private HttpParameters add(String key, String value, boolean percentEncode) {
        try {
            String k = percentEncode ? percentEncoder.encode(key) : key;
            String v = percentEncode ? percentEncoder.encode(value) : value;
            super.add(k, v);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        }
        return this;
    }

    public String getAsQueryString(boolean percentEncode) throws MalformedInputException, UnmappableCharacterException {
        List<String> list = new ArrayList<>();
        for (String key : super.names()) {
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
        Collection<String> values = getAll(k);
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
}
