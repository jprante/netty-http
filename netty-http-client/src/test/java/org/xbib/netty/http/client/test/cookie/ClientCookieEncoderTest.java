package org.xbib.netty.http.client.test.cookie;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.cookie.ClientCookieEncoder;
import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.DefaultCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientCookieEncoderTest {

    @Test
    void testEncodingMultipleClientCookies() {
        String c1 = "myCookie=myValue";
        String c2 = "myCookie2=myValue2";
        String c3 = "myCookie3=myValue3";
        Cookie cookie1 = new DefaultCookie("myCookie", "myValue");
        cookie1.setDomain(".adomainsomewhere");
        cookie1.setMaxAge(50);
        cookie1.setPath("/apathsomewhere");
        cookie1.setSecure(true);
        Cookie cookie2 = new DefaultCookie("myCookie2", "myValue2");
        cookie2.setDomain(".anotherdomainsomewhere");
        cookie2.setPath("/anotherpathsomewhere");
        cookie2.setSecure(false);
        Cookie cookie3 = new DefaultCookie("myCookie3", "myValue3");
        String encodedCookie = ClientCookieEncoder.STRICT.encode(cookie1, cookie2, cookie3);
        // Cookies should be sorted into decreasing order of path length, as per RFC6265.
        // When no path is provided, we assume maximum path length (so cookie3 comes first).
        assertEquals(c3 + "; " + c2 + "; " + c1, encodedCookie);
    }

    @Test
    void testWrappedCookieValue() {
        ClientCookieEncoder.STRICT.encode(new DefaultCookie("myCookie", "\"foo\""));
    }

    @Test
    void testRejectCookieValueWithSemicolon() {
        assertThrows(IllegalArgumentException.class, () ->
                ClientCookieEncoder.STRICT.encode(new DefaultCookie("myCookie", "foo;bar")));
    }
}
