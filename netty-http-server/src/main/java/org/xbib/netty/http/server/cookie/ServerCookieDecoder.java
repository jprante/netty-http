package org.xbib.netty.http.server.cookie;

import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.CookieDecoder;
import org.xbib.netty.http.common.cookie.CookieHeaderNames;
import org.xbib.netty.http.common.cookie.DefaultCookie;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used server side.
 *
 * Only name and value fields are expected, so old fields are not populated (path, domain, etc).
 *
 * Old <a href="http://tools.ietf.org/html/rfc2965">RFC2965</a> cookies are still supported,
 * old fields will simply be ignored.
 */
public final class ServerCookieDecoder extends CookieDecoder {

    private static final String RFC2965_VERSION = "$Version";

    private static final String RFC2965_PATH = "$" + CookieHeaderNames.PATH;

    private static final String RFC2965_DOMAIN = "$" + CookieHeaderNames.DOMAIN;

    private static final String RFC2965_PORT = "$Port";

    /**
     * Strict encoder that validates that name and value chars are in the valid scope
     * defined in RFC6265
     */
    public static final ServerCookieDecoder STRICT = new ServerCookieDecoder(true);

    /**
     * Lax instance that doesn't validate name and value
     */
    public static final ServerCookieDecoder LAX = new ServerCookieDecoder(false);

    private ServerCookieDecoder(boolean strict) {
        super(strict);
    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a {@link Cookie}.
     *
     * @param header header
     * @return the decoded {@link Cookie}
     */
    public Set<Cookie> decode(String header) {
        final int headerLen = Objects.requireNonNull(header, "header").length();
        if (headerLen == 0) {
            return Collections.emptySet();
        }
        Set<Cookie> cookies = new LinkedHashSet<>();
        int i = 0;
        boolean rfc2965Style = false;
        if (header.regionMatches(true, 0, RFC2965_VERSION, 0, RFC2965_VERSION.length())) {
            // RFC 2965 style cookie, move to after version value
            i = header.indexOf(';') + 1;
            rfc2965Style = true;
        }
        while (i < headerLen) {
            // Skip spaces and separators.
            while (i < headerLen) {
                char c = header.charAt(i);
                switch (c) {
                    case '\t':
                    case '\n':
                    case 0x0b:
                    case '\f':
                    case '\r':
                    case ' ':
                    case ',':
                    case ';':
                        i++;
                        continue;
                    default:
                        break;
                }
                break;
            }
            int nameBegin = i;
            int nameEnd = 0;
            int valueBegin = 0;
            int valueEnd = 0;
            while (i < headerLen) {
                char curChar = header.charAt(i);
                if (curChar == ';') {
                    // NAME; (no value till ';')
                    nameEnd = i;
                    valueBegin = valueEnd = -1;
                    break;

                } else if (curChar == '=') {
                    // NAME=VALUE
                    nameEnd = i;
                    i++;
                    if (i == headerLen) {
                        // NAME= (empty value, i.e. nothing after '=')
                        valueBegin = valueEnd = 0;
                        break;
                    }
                    valueBegin = i;
                    int semiPos = header.indexOf(';', i);
                    valueEnd = i = semiPos > 0 ? semiPos : headerLen;
                    break;
                } else {
                    i++;
                }
                if (i == headerLen) {
                    // NAME (no value till the end of string)
                    nameEnd = headerLen;
                    valueBegin = valueEnd = -1;
                    break;
                }
            }
            if (rfc2965Style && (header.regionMatches(nameBegin, RFC2965_PATH, 0, RFC2965_PATH.length()) ||
                    header.regionMatches(nameBegin, RFC2965_DOMAIN, 0, RFC2965_DOMAIN.length()) ||
                    header.regionMatches(nameBegin, RFC2965_PORT, 0, RFC2965_PORT.length()))) {
                // skip obsolete RFC2965 fields
                continue;
            }
            if (nameEnd >= nameBegin && valueEnd >= valueBegin) {
                DefaultCookie cookie = initCookie(header, nameBegin, nameEnd, valueBegin, valueEnd);
                if (cookie != null) {
                    cookies.add(cookie);
                }
            }
        }
        return cookies;
    }
}
