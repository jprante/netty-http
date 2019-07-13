package org.xbib.netty.http.client.cookie;

import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.CookieDecoder;
import org.xbib.netty.http.common.cookie.CookieHeaderNames;
import org.xbib.netty.http.common.cookie.DefaultCookie;
import org.xbib.netty.http.common.util.DateTimeUtils;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * A <a href="http://tools.ietf.org/html/rfc6265">RFC6265</a> compliant cookie decoder to be used client side.
 *
 * It will store the way the raw value was wrapped in {@link Cookie#setWrap(boolean)} so it can be
 * eventually sent back to the Origin server as is.
 *
 */
public final class ClientCookieDecoder extends CookieDecoder {

    /**
     * Strict encoder that validates that name and value chars are in the valid scope defined in RFC6265.
     */
    public static final ClientCookieDecoder STRICT = new ClientCookieDecoder(true);

    /**
     * Lax instance that doesn't validate name and value.
     */
    public static final ClientCookieDecoder LAX = new ClientCookieDecoder(false);

    private ClientCookieDecoder(boolean strict) {
        super(strict);
    }

    /**
     * Decodes the specified Set-Cookie HTTP header value into a {@link Cookie}.
     *
     * @param header header
     * @return the decoded {@link Cookie}
     */
    public Cookie decode(String header) {
        final int headerLen = Objects.requireNonNull(header, "header").length();
        if (headerLen == 0) {
            return null;
        }
        CookieBuilder cookieBuilder = null;
        int i = 0;
        while (i < headerLen) {
            while (i < headerLen) {
                // Skip spaces and separators.
                char c = header.charAt(i);
                if (c == ',') {
                    // Having multiple cookies in a single Set-Cookie header is
                    // deprecated, modern browsers only parse the first one
                    break;
                } else {
                    switch (c) {
                        case '\t':
                        case '\n':
                        case 0x0b:
                        case '\f':
                        case '\r':
                        case ' ':
                        case ';':
                            i++;
                            continue;
                        default:
                            break;
                    }
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
                    nameEnd = i;
                    valueBegin = valueEnd = -1;
                    break;
                } else if (curChar == '=') {
                    nameEnd = i;
                    i++;
                    if (i == headerLen) {
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
                    nameEnd = headerLen;
                    valueBegin = valueEnd = -1;
                    break;
                }
            }
            if (valueEnd > 0 && header.charAt(valueEnd - 1) == ',') {
                valueEnd--;
            }
            if (nameEnd >= nameBegin && valueEnd >= valueBegin) {
                if (cookieBuilder == null) {
                    DefaultCookie cookie = initCookie(header, nameBegin, nameEnd, valueBegin, valueEnd);
                    if (cookie == null) {
                        return null;
                    }
                    cookieBuilder = new CookieBuilder(cookie, header);
                } else {
                    cookieBuilder.appendAttribute(nameBegin, nameEnd, valueBegin, valueEnd);
                }
            }
        }
        return cookieBuilder != null ? cookieBuilder.cookie() : null;
    }

    private static class CookieBuilder {

        private final String header;

        private final DefaultCookie cookie;

        private String domain;

        private String path;

        private long maxAge = Long.MIN_VALUE;

        private int expiresStart;

        private int expiresEnd;

        private boolean secure;

        private boolean httpOnly;

        private Cookie.SameSite sameSite = Cookie.SameSite.STRICT;

        CookieBuilder(DefaultCookie cookie, String header) {
            this.cookie = cookie;
            this.header = header;
        }

        Cookie cookie() {
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(mergeMaxAgeAndExpires());
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
            cookie.setSameSite(sameSite);
            return cookie;
        }

        /**
         * Parse and store a key-value pair. First one is considered to be the
         * cookie name/value. Unknown attribute names are silently discarded.
         *
         * @param keyStart
         *            where the key starts in the header
         * @param keyEnd
         *            where the key ends in the header
         * @param valueStart
         *            where the value starts in the header
         * @param valueEnd
         *            where the value ends in the header
         */
        void appendAttribute(int keyStart, int keyEnd, int valueStart, int valueEnd) {
            int length = keyEnd - keyStart;
            if (length == 4) {
                parse4(keyStart, valueStart, valueEnd);
            } else if (length == 6) {
                parse6(keyStart, valueStart, valueEnd);
            } else if (length == 7) {
                parse7(keyStart, valueStart, valueEnd);
            } else if (length == 8) {
                parse8(keyStart, valueStart, valueEnd);
            }
        }

        private void parse4(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.PATH, 0, 4)) {
                path = computeValue(valueStart, valueEnd);
            }
        }

        private void parse6(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.DOMAIN, 0, 5)) {
                domain = computeValue(valueStart, valueEnd);
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SECURE, 0, 5)) {
                secure = true;
            }
        }

        private void setMaxAge(String value) {
            try {
                maxAge = Math.max(Long.parseLong(value), 0L);
            } catch (NumberFormatException e1) {
                // ignore failure to parse -> treat as session cookie
            }
        }

        private long mergeMaxAgeAndExpires() {
            if (maxAge != Long.MIN_VALUE) {
                return maxAge;
            } else if (isValueDefined(expiresStart, expiresEnd)) {
                Instant expiresDate = DateTimeUtils.parseDate(header, expiresStart, expiresEnd);
                if (expiresDate != null) {
                    Instant now = Instant.now();
                    long maxAgeMillis = expiresDate.toEpochMilli() - now.toEpochMilli();
                    return maxAgeMillis / 1000 + (maxAgeMillis % 1000 != 0 ? 1 : 0);
                }
            }
            return Long.MIN_VALUE;
        }

        private void parse7(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.EXPIRES, 0, 7)) {
                expiresStart = valueStart;
                expiresEnd = valueEnd;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.MAX_AGE, 0, 7)) {
                setMaxAge(computeValue(valueStart, valueEnd));
            }
        }

        private void parse8(int nameStart, int valueStart, int valueEnd) {
            if (header.regionMatches(true, nameStart, CookieHeaderNames.HTTPONLY, 0, 8)) {
                httpOnly = true;
            } else if (header.regionMatches(true, nameStart, CookieHeaderNames.SAMESITE, 0, 8)) {
                String string = computeValue(valueStart, valueEnd);
                if (string != null) {
                    setSameSite(Cookie.SameSite.valueOf(string.toUpperCase(Locale.ROOT)));
                }
            }
        }

        private static boolean isValueDefined(int valueStart, int valueEnd) {
            return valueStart != -1 && valueStart != valueEnd;
        }

        private String computeValue(int valueStart, int valueEnd) {
            return isValueDefined(valueStart, valueEnd) ? header.substring(valueStart, valueEnd) : null;
        }

        private void setSameSite(Cookie.SameSite value) {
            sameSite = value;
        }
    }
}
