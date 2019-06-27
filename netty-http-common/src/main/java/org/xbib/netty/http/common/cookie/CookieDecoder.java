package org.xbib.netty.http.common.cookie;

import java.nio.CharBuffer;

/**
 * Parent of Client and Server side cookie decoders.
 */
public abstract class CookieDecoder {

    protected final boolean strict;

    protected CookieDecoder(boolean strict) {
        this.strict = strict;
    }

    protected DefaultCookie initCookie(String header, int nameBegin, int nameEnd, int valueBegin, int valueEnd) {
        if (nameBegin == -1 || nameBegin == nameEnd) {
            return null;
        }
        if (valueBegin == -1) {
            return null;
        }
        CharSequence wrappedValue = CharBuffer.wrap(header, valueBegin, valueEnd);
        CharSequence unwrappedValue = CookieUtil.unwrapValue(wrappedValue);
        if (unwrappedValue == null) {
            return null;
        }
        String name = header.substring(nameBegin, nameEnd);
        if (strict && CookieUtil.firstInvalidCookieNameOctet(name) >= 0) {
            return null;
        }
        final boolean wrap = unwrappedValue.length() != valueEnd - valueBegin;
        if (strict && CookieUtil.firstInvalidCookieValueOctet(unwrappedValue) >= 0) {
            return null;
        }
        DefaultCookie cookie = new DefaultCookie(name, unwrappedValue.toString());
        cookie.setWrap(wrap);
        return cookie;
    }
}
