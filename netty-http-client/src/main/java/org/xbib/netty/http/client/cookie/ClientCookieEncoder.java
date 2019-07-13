package org.xbib.netty.http.client.cookie;

import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.CookieEncoder;
import org.xbib.netty.http.common.cookie.CookieUtil;
import org.xbib.netty.http.common.cookie.DefaultCookie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie encoder to be used client side, so
 * only name=value pairs are sent.
 *
 * Note that multiple cookies are supposed to be sent at once in a single "Cookie" header.
 *
 * <pre>
 * // Example
 * HttpRequest req = ...
 * res.setHeader("Cookie", {@link ClientCookieEncoder}.encode("JSESSIONID", "1234"))
 * </pre>
 *
 */
public final class ClientCookieEncoder extends CookieEncoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope and (for methods that accept
     * multiple cookies) sorts cookies into order of decreasing path length, as specified in RFC6265.
     */
    public static final ClientCookieEncoder STRICT = new ClientCookieEncoder(true);

    /**
     * Lax instance that doesn't validate name and value, and (for methods that accept multiple cookies) keeps
     * cookies in the order in which they were given.
     */
    public static final ClientCookieEncoder LAX = new ClientCookieEncoder(false);

    private ClientCookieEncoder(boolean strict) {
        super(strict);
    }

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param name
     *            the cookie name
     * @param value
     *            the cookie value
     * @return a Rfc6265 style Cookie header value
     */
    public String encode(String name, String value) {
        return encode(new DefaultCookie(name, value));
    }

    /**
     * Encodes the specified cookie into a Cookie header value.
     *
     * @param cookie the specified cookie
     * @return a Rfc6265 style Cookie header value
     */
    public String encode(Cookie cookie) {
        StringBuilder buf = new StringBuilder();
        encode(buf, Objects.requireNonNull(cookie, "cookie"));
        return CookieUtil.stripTrailingSeparator(buf);
    }

    /**
     * Sort cookies into decreasing order of path length, breaking ties by sorting into increasing chronological
     * order of creation time, as recommended by RFC 6265.
     */
    private static final Comparator<Cookie> COOKIE_COMPARATOR = (c1, c2) -> {
        String path1 = c1.path();
        String path2 = c2.path();
        // Cookies with unspecified path default to the path of the request. We don't
        // know the request path here, but we assume that the length of an unspecified
        // path is longer than any specified path (i.e. pathless cookies come first),
        // because setting cookies with a path longer than the request path is of
        // limited use.
        int len1 = path1 == null ? Integer.MAX_VALUE : path1.length();
        int len2 = path2 == null ? Integer.MAX_VALUE : path2.length();
        int diff = len2 - len1;
        if (diff != 0) {
            return diff;
        }
        // Rely on Java's sort stability to retain creation order in cases where
        // cookies have same path length.
        return -1;
    };

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies
     *            some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Cookie... cookies) {
        if (Objects.requireNonNull(cookies, "cookies").length == 0) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        if (strict) {
            if (cookies.length == 1) {
                encode(buf, cookies[0]);
            } else {
                Cookie[] cookiesSorted = Arrays.copyOf(cookies, cookies.length);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            for (Cookie c : cookies) {
                encode(buf, c);
            }
        }
        return CookieUtil.stripTrailingSeparatorOrNull(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies
     *            some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Collection<? extends Cookie> cookies) {
        if (Objects.requireNonNull(cookies, "cookies").isEmpty()) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        if (strict) {
            if (cookies.size() == 1) {
                encode(buf, cookies.iterator().next());
            } else {
                Cookie[] cookiesSorted = cookies.toArray(new Cookie[cookies.size()]);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            for (Cookie c : cookies) {
                encode(buf, c);
            }
        }
        return CookieUtil.stripTrailingSeparatorOrNull(buf);
    }

    /**
     * Encodes the specified cookies into a single Cookie header value.
     *
     * @param cookies some cookies
     * @return a Rfc6265 style Cookie header value, null if no cookies are passed.
     */
    public String encode(Iterable<? extends Cookie> cookies) {
        Iterator<? extends Cookie> cookiesIt = Objects.requireNonNull(cookies, "cookies").iterator();
        if (!cookiesIt.hasNext()) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        if (strict) {
            Cookie firstCookie = cookiesIt.next();
            if (!cookiesIt.hasNext()) {
                encode(buf, firstCookie);
            } else {
                List<Cookie> cookiesList = new ArrayList<>();
                cookiesList.add(firstCookie);
                while (cookiesIt.hasNext()) {
                    cookiesList.add(cookiesIt.next());
                }
                Cookie[] cookiesSorted = cookiesList.toArray(new Cookie[0]);
                Arrays.sort(cookiesSorted, COOKIE_COMPARATOR);
                for (Cookie c : cookiesSorted) {
                    encode(buf, c);
                }
            }
        } else {
            while (cookiesIt.hasNext()) {
                encode(buf, cookiesIt.next());
            }
        }
        return CookieUtil.stripTrailingSeparatorOrNull(buf);
    }

    public void encode(StringBuilder buf, Cookie c) {
        final String name = c.name();
        final String value = c.value() != null ? c.value() : "";
        validateCookie(name, value);
        if (c.wrap()) {
            CookieUtil.addQuoted(buf, name, value);
        } else {
            CookieUtil.add(buf, name, value);
        }
    }
}
