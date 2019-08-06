package org.xbib.netty.http.common.test.security;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.security.CryptUtil;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptUtilTest {

    @Test
    void testRfc2307() throws NoSuchAlgorithmException {
        assertEquals("{md5}ixqZU8RhEpaoJ6v4xHgE1w==",
                CryptUtil.md5("Hello"));
        assertEquals("{sha}9/+ei3uy4Jtwk1pdeF4MxdnQq/A=",
                CryptUtil.sha("Hello"));
        assertEquals("{sha256}GF+NsyJx/iX1Yab8k4suJkMG7DBO2lGAB9F2SCY4GWk=",
                CryptUtil.sha256("Hello"));
        assertEquals("{sha512}NhX4DJ0pPtdAJof5SyLVjlKbjMeRb4+sf933+9WvTPd309eVp6AKFr9+fz+5Vh7puq5IDan+ehh2nnGIawPzFQ==",
                CryptUtil.sha512("Hello"));
    }

    @Test
    void testHmac() throws InvalidKeyException, NoSuchAlgorithmException {
        assertEquals("Wgxn2SLeDKU+MGJQ5oWMH20sSUM=",
                CryptUtil.hmacSHA1(StandardCharsets.ISO_8859_1, "hello", "world"));
        assertEquals("PPp27xSTfBwOpRn4/AV6gPzQSnQg+Oi80KdWfCcuAHs=",
                CryptUtil.hmacSHA256(StandardCharsets.ISO_8859_1, "hello", "world"));
    }
}
