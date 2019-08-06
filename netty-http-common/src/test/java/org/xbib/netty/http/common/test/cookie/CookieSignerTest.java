package org.xbib.netty.http.common.test.cookie;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.cookie.DefaultCookie;
import org.xbib.netty.http.common.cookie.CookieSigner;
import org.xbib.netty.http.common.security.HMac;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CookieSignerTest {

    @Test
    void testEncodeSignedCookie() {
        String id = "dummy";
        CookieSigner cookieSigner = CookieSigner.builder().withHMac(HMac.HMAC_SHA256)
                .withPublicValue(id)
                .withPrivateValue(Base64.getEncoder().encodeToString("Hello".getBytes(StandardCharsets.UTF_8)))
                .withSecret("d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb")
                .build();
        DefaultCookie cookie = new DefaultCookie("SESS", cookieSigner);
        assertEquals("dummy", cookieSigner.getPublicValue());
        assertEquals("dummy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D", cookie.value());
    }

    @Test
    void testDecodeSignedCookie() {
        String rawValue = "dummy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D";
        CookieSigner cookieSigner = CookieSigner.builder()
                .withHMac(HMac.HMAC_SHA256)
                .withRawValue(rawValue)
                .withSecret("d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb")
                .build();
        DefaultCookie cookie = new DefaultCookie("SESS", cookieSigner);
        assertEquals("dummy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D", cookie.value());
        assertEquals("dummy", cookieSigner.getPublicValue());
        assertEquals("Hello", new String(Base64.getDecoder().decode(cookieSigner.getPrivateValue()),
                StandardCharsets.UTF_8));
    }

    @Test
    void testDecodeInvalidTuple() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String rawValue = "dummy%3JSGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D";
            CookieSigner.builder()
                    .withHMac(HMac.HMAC_SHA256)
                    .withRawValue(rawValue)
                    .withSecret("d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb")
                    .build();

        });
    }

    @Test
    void testDecodeISOGivesMalformedInputException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            String rawValue = "dummy%FCSGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D";
            CookieSigner.builder()
                    .withHMac(HMac.HMAC_SHA256)
                    .withRawValue(rawValue)
                    .withSecret("d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb")
                    .build();

        });
    }

    @Test
    void testDecodeISO() {
        String rawValue = "d%FCmmy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D";
        CookieSigner cookieSigner = CookieSigner.builder()
                .withCharset(StandardCharsets.ISO_8859_1)
                .withHMac(HMac.HMAC_SHA256)
                .withRawValue(rawValue)
                .withSecret("d9fd3a19707be6025b4f5a98c320745960a1b95144f040afcda2a4997dbae0cb")
                .build();
        DefaultCookie cookie = new DefaultCookie("SESS", cookieSigner);
        assertEquals("d%FCmmy%3ASGVsbG8%3D%3AqEdbImuwfh7r%2BmOaShC3IjXhkdiiF3Y1RgSZ%2FFAZrQ4%3D", cookie.value());
        assertEquals("dümmy", cookieSigner.getPublicValue());
        assertEquals("Hello", new String(Base64.getDecoder().decode(cookieSigner.getPrivateValue()),
                StandardCharsets.UTF_8));
    }
}
