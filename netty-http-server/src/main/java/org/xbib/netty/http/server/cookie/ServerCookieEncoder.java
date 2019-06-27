package org.xbib.netty.http.server.cookie;

import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.CookieEncoder;
import org.xbib.netty.http.common.cookie.CookieHeaderNames;
import org.xbib.netty.http.common.cookie.CookieUtil;
import org.xbib.netty.http.common.cookie.DefaultCookie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used server side,
 * so some fields are sent (Version is typically ignored).
 *
 * As Cookie merges Expires and MaxAge into one single field, only Max-Age field is sent.
 *
 * Note that multiple cookies are supposed to be sent at once in a single "Set-Cookie" header.
 *
 * <pre>
 * // Example
 * HttpRequest req = ...
 * res.withHeader("Cookie", {@link ServerCookieEncoder}.encode("JSESSIONID", "1234"))
 * </pre>
 *
 * @see ServerCookieDecoder
 */
public final class ServerCookieEncoder extends CookieEncoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope
     * defined in RFC6265, and (for methods that accept multiple cookies) that only
     * one cookie is encoded with any given name. (If multiple cookies have the same
     * name, the last one is the one that is encoded.)
     */
    public static final ServerCookieEncoder STRICT = new ServerCookieEncoder(true);

    /**
     * Lax instance that doesn't validate name and value, and that allows multiple
     * cookies with the same name.
     */
    public static final ServerCookieEncoder LAX = new ServerCookieEncoder(false);

    private ServerCookieEncoder(boolean strict) {
        super(strict);
    }

    /**
     * Encodes the specified cookie name-value pair into a Set-Cookie header value.
     *
     * @param name the cookie name
     * @param value the cookie value
     * @return a single Set-Cookie header value
     */
    public String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Set-Cookie header value.
     *
     * @param cookie the cookie
     * @return a single Set-Cookie header value
     */
    public String encode(Cookie cookie) {
        final String name = Objects.requireNonNull(cookie, "cookie").name();
        final String value = cookie.value() != null ? cookie.value() : "";
        validateCookie(name, value);
        StringBuilder buf = new StringBuilder();
        if (cookie.wrap()) {
            CookieUtil.addQuoted(buf, name, value);
        } else {
            CookieUtil.add(buf, name, value);
        }
        if (cookie.maxAge() != Long.MIN_VALUE) {
            CookieUtil.add(buf, CookieHeaderNames.MAX_AGE, cookie.maxAge());
            //Date expires = new Date(cookie.maxAge() * 1000 + System.currentTimeMillis())
            buf.append(CookieHeaderNames.EXPIRES);
            buf.append(CookieUtil.EQUALS);
            //DateFormatter.append(expires, buf)
            buf.append(CookieUtil.SEMICOLON);
            buf.append(CookieUtil.SP);
        }
        if (cookie.path() != null) {
            CookieUtil.add(buf, CookieHeaderNames.PATH, cookie.path());
        }
        if (cookie.domain() != null) {
            CookieUtil.add(buf, CookieHeaderNames.DOMAIN, cookie.domain());
        }
        if (cookie.isSecure()) {
            CookieUtil.add(buf, CookieHeaderNames.SECURE);
        }
        if (cookie.isHttpOnly()) {
            CookieUtil.add(buf, CookieHeaderNames.HTTPONLY);
        }
        if (cookie.sameSite() != null) {
            CookieUtil.add(buf, CookieHeaderNames.SAMESITE, cookie.sameSite());
        }
        return CookieUtil.stripTrailingSeparator(buf);
    }

    /**
     * Deduplicate a list of encoded cookies by keeping only the last instance with a given name.
     *
     * @param encoded The list of encoded cookies.
     * @param nameToLastIndex A map from cookie name to index of last cookie instance.
     * @return The encoded list with all but the last instance of a named cookie.
     */
    private static List<String> dedup(List<String> encoded, Map<String, Integer> nameToLastIndex) {
        boolean[] isLastInstance = new boolean[encoded.size()];
        for (int idx : nameToLastIndex.values()) {
            isLastInstance[idx] = true;
        }
        List<String> dedupd = new ArrayList<>(nameToLastIndex.size());
        int n = encoded.size();
        for (int i = 0; i < n; i++) {
            if (isLastInstance[i]) {
                dedupd.add(encoded.get(i));
            }
        }
        return dedupd;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Cookie... cookies) {
        if (Objects.requireNonNull(cookies, "cookies").length == 0) {
            return Collections.emptyList();
        }
        List<String> encoded = new ArrayList<>(cookies.length);
        Map<String, Integer> nameToIndex = strict && cookies.length > 1 ? new LinkedHashMap<>() : null;
        boolean hasDupdName = false;
        for (int i = 0; i < cookies.length; i++) {
            Cookie c = cookies[i];
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Collection<? extends Cookie> cookies) {
        if (Objects.requireNonNull(cookies, "cookies").isEmpty()) {
            return Collections.emptyList();
        }
        List<String> encoded = new ArrayList<>(cookies.size());
        Map<String, Integer> nameToIndex = strict && cookies.size() > 1 ? new LinkedHashMap<>() : null;
        int i = 0;
        boolean hasDupdName = false;
        for (Cookie c : cookies) {
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }

    /**
     * Batch encodes cookies into Set-Cookie header values.
     *
     * @param cookies a bunch of cookies
     * @return the corresponding bunch of Set-Cookie headers
     */
    public List<String> encode(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = Objects.requireNonNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return Collections.emptyList();
        }
        List<String> encoded = new ArrayList<>();
        Cookie firstCookie = cookiesIt.next();
        Map<String, Integer> nameToIndex = strict && cookiesIt.hasNext() ? new LinkedHashMap<>() : null;
        int i = 0;
        encoded.add(encode(firstCookie));
        boolean hasDupdName = nameToIndex != null && (nameToIndex.put(firstCookie.name(), i++) != null);
        while (cookiesIt.hasNext()) {
            Cookie c = cookiesIt.next();
            encoded.add(encode(c));
            if (nameToIndex != null) {
                hasDupdName |= nameToIndex.put(c.name(), i++) != null;
            }
        }
        return hasDupdName ? dedup(encoded, nameToIndex) : encoded;
    }
}
