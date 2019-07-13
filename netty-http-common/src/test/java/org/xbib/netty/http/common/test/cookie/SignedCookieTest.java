package org.xbib.netty.http.common.test.cookie;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.DefaultCookie;
import org.xbib.netty.http.common.cookie.Payload;
import org.xbib.netty.http.common.util.Codec;
import org.xbib.netty.http.common.util.HMac;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignedCookieTest {

    @Test
    void testEncodeDefaultCookie() {
        Base64Codec codec = new Base64Codec();
        String cookieName = "SESS";
        String domain = ".hbz-nrw.de";
        String path = "/";
        String id = "dummy";
        Payload payload = new Payload(Codec.BASE64, HMac.HMAC_SHA256,
                id, new String(codec.encode("Hello"), StandardCharsets.UTF_8),
                "d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb");
        DefaultCookie cookie = new DefaultCookie(cookieName, payload);
        cookie.setDomain(domain);
        cookie.setPath(path);
        cookie.setMaxAge(3600);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setSameSite(Cookie.SameSite.LAX);
        assertEquals("dummy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D", cookie.value());
        assertEquals(domain, cookie.domain());
        assertEquals(path, cookie.path());
        assertEquals(3600, cookie.maxAge());
        assertEquals(cookieName, cookie.name());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals(Cookie.SameSite.LAX, cookie.sameSite());
    }

    @Test
    void testCookieValue()
            throws MalformedInputException, UnmappableCharacterException, NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Base64Codec codec = new Base64Codec();
        String rawCookieValue = "dummy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D";
        Payload payload = new Payload(Codec.BASE64, HMac.HMAC_SHA256,
                rawCookieValue, "d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb");
        DefaultCookie cookie = new DefaultCookie("SESS", payload);
        assertEquals("dummy", payload.getPublicValue());
        assertEquals("Hello", codec.decode(payload.getPrivateValue().getBytes(StandardCharsets.UTF_8)));
    }

    class Base64Codec {

        byte[] encode(String payload) {
            return Base64.getEncoder().encode(payload.getBytes(StandardCharsets.UTF_8));
        }

        String decode(byte[] bytes) {
            return new String(Base64.getDecoder().decode(bytes), StandardCharsets.UTF_8);
        }
    }
}
