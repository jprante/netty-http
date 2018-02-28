package org.xbib.netty.http.client.test;

import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class HttpBinTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger(HttpBinTest.class.getName());

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
     * @throws Exception
     */
    @Test
    public void testHttpBinCookies() {
        Client client = new Client();
        try {
            Request request = Request.get()
                    .setURL("http://httpbin.org/cookies/set?name=value")
                    .build()
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                    .setCookieListener(cookie -> logger.log(Level.INFO, "this is the cookie " + cookie.toString()))
                    .setHeadersListener(headers -> logger.log(Level.INFO, headers.toString()))
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
