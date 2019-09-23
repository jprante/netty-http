package org.xbib.netty.http.client.test.cookie;

import static org.junit.Assert.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;
import org.xbib.netty.http.common.cookie.Cookie;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class CookieSetterHttpBinTest {

    private static final Logger logger = Logger.getLogger(CookieSetterHttpBinTest.class.getName());

    /**
     * Test httpbin.org "Set-Cookie:" header after redirection of URL.
     *
     * The reponse body should be
     * <pre>
     *   {
     *     "cookies": {
     *       "name": "value"
     *     }
     *   }
     * </pre>
     * @throws IOException if test fails
     */
    @Test
    void testHttpBinCookies() throws IOException {
        Client client = new Client();
        AtomicBoolean success = new AtomicBoolean();
        try {
            Request request = Request.get()
                    .url("http://httpbin.org/cookies/set?name=value")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "status = " + resp.getStatus() +
                                " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
                        for (Cookie cookie : resp.getCookies().keySet()) {
                            logger.log(Level.INFO, "got cookie: " + cookie.toString());
                            if ("name".equals(cookie.name()) && ("value".equals(cookie.value()))) {
                                success.set(true);
                            }
                        }
                    })
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
        assertTrue(success.get());
    }
}
