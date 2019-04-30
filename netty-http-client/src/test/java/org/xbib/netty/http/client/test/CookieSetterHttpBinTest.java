package org.xbib.netty.http.client.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpExtension.class)
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
        try {
            Request request = Request.get()
                    .url("http://httpbin.org/cookies/set?name=value")
                    .build()
                    .setCookieListener(cookie -> logger.log(Level.INFO, "this is the cookie: " + cookie.toString()))
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }
}
