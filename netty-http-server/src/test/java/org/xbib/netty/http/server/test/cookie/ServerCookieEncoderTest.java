package org.xbib.netty.http.server.test.cookie;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.cookie.ClientCookieEncoder;
import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.DefaultCookie;
import org.xbib.netty.http.common.util.DateTimeUtil;
import org.xbib.netty.http.server.cookie.ServerCookieEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCookieEncoderTest {

    @Test
    void testEncodingSingleCookieV0() {
        int maxAge = 50;
        String result = "myCookie=myValue; Max-Age=50; Expires=(.+?); Path=/apathsomewhere; Domain=.adomainsomewhere; Secure";
        Cookie cookie = new DefaultCookie("myCookie", "myValue");
        cookie.setDomain(".adomainsomewhere");
        cookie.setMaxAge(maxAge);
        cookie.setPath("/apathsomewhere");
        cookie.setSecure(true);
        String encodedCookie = ServerCookieEncoder.STRICT.encode(cookie);
        Matcher matcher = Pattern.compile(result).matcher(encodedCookie);
        assertTrue(matcher.find());
        Instant expire = DateTimeUtil.parseDate(matcher.group(1));
        long diff = (expire.toEpochMilli() - System.currentTimeMillis()) / 1000;
        assertTrue(Math.abs(diff - maxAge) <= 2);
    }

    @Test
    void testEncodingWithNoCookies() {
        String encodedCookie1 = ClientCookieEncoder.STRICT.encode(Collections.emptyList());
        List<String> encodedCookie2 = ServerCookieEncoder.STRICT.encode(Collections.emptyList());
        assertNull(encodedCookie1);
        assertNotNull(encodedCookie2);
        assertTrue(encodedCookie2.isEmpty());
    }

    @Test
    void testEncodingMultipleCookiesStrict() {
        List<String> result = new ArrayList<>();
        result.add("cookie2=value2");
        result.add("cookie1=value3");
        Cookie cookie1 = new DefaultCookie("cookie1", "value1");
        Cookie cookie2 = new DefaultCookie("cookie2", "value2");
        Cookie cookie3 = new DefaultCookie("cookie1", "value3");
        List<String> encodedCookies = ServerCookieEncoder.STRICT.encode(cookie1, cookie2, cookie3);
        assertEquals(result, encodedCookies);
    }

    @Test
    void illegalCharInCookieNameMakesStrictEncoderThrowsException() {
        Set<Character> illegalChars = new LinkedHashSet<>();
        // CTLs
        for (char i = 0x00; i <= 0x1F; i++) {
            illegalChars.add(i);
        }
        illegalChars.add((char) 0x7F);
        // separators
        for (char c : new char[] {'(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']',
                '?', '=', '{', '}', ' ', '\t' }) {
            illegalChars.add(c);
        }
        int exceptions = 0;
        for (char c : illegalChars) {
            try {
                ServerCookieEncoder.STRICT.encode(new DefaultCookie("foo" + c + "bar", "value"));
            } catch (IllegalArgumentException e) {
                exceptions++;
            }
        }
       assertEquals(illegalChars.size(), exceptions);
    }

    @Test
    void illegalCharInCookieValueMakesStrictEncoderThrowsException() {
        Set<Character> illegalChars = new LinkedHashSet<>();
        // CTLs
        for (char i = 0x00; i <= 0x1F; i++) {
            illegalChars.add(i);
        }
        illegalChars.add((char) 0x7F);
        // whitespace, DQUOTE, comma, semicolon, and backslash
        for (char c : new char[] { ' ', '"', ',', ';', '\\' }) {
            illegalChars.add(c);
        }
        int exceptions = 0;
        for (char c : illegalChars) {
            try {
                ServerCookieEncoder.STRICT.encode(new DefaultCookie("name", "value" + c));
            } catch (IllegalArgumentException e) {
                exceptions++;
            }
        }
       assertEquals(illegalChars.size(), exceptions);
    }

    @Test
    void testEncodingMultipleCookiesLax() {
        List<String> result = new ArrayList<>();
        result.add("cookie1=value1");
        result.add("cookie2=value2");
        result.add("cookie1=value3");
        Cookie cookie1 = new DefaultCookie("cookie1", "value1");
        Cookie cookie2 = new DefaultCookie("cookie2", "value2");
        Cookie cookie3 = new DefaultCookie("cookie1", "value3");
        List<String> encodedCookies = ServerCookieEncoder.LAX.encode(cookie1, cookie2, cookie3);
       assertEquals(result, encodedCookies);
    }
}
