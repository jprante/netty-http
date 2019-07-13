package org.xbib.netty.http.common.test;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.util.CryptUtils;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptUtilsTest {

    @Test
    void testRfc2307() throws NoSuchAlgorithmException {

        assertEquals("{md5}ixqZU8RhEpaoJ6v4xHgE1w==",
                CryptUtils.md5("Hello"));
        assertEquals("{sha}9/+ei3uy4Jtwk1pdeF4MxdnQq/A=",
                CryptUtils.sha("Hello"));
        assertEquals("{sha256}GF+NsyJx/iX1Yab8k4suJkMG7DBO2lGAB9F2SCY4GWk=",
                CryptUtils.sha256("Hello"));
        assertEquals("{sha512}NhX4DJ0pPtdAJof5SyLVjlKbjMeRb4+sf933+9WvTPd309eVp6AKFr9+fz+5Vh7puq5IDan+ehh2nnGIawPzFQ==",
                CryptUtils.sha512("Hello"));
    }

    @Test
    void testHmac() throws InvalidKeyException, NoSuchAlgorithmException {
        assertEquals("Wgxn2SLeDKU+MGJQ5oWMH20sSUM=",
                CryptUtils.hmacSHA1("hello", "world"));
        assertEquals("PPp27xSTfBwOpRn4/AV6gPzQSnQg+Oi80KdWfCcuAHs=",
                CryptUtils.hmacSHA256("hello", "world"));
    }
}
