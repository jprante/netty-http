package org.xbib.netty.http.client.test;

import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConscryptTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testConscrypt() {
        Client client = Client.builder()
                .enableDebug()
                .setJdkSslProvider()
                .setSslContextProvider(Conscrypt.newProvider())
                .build();
        logger.log(Level.INFO, client.getClientConfig().toString());
        try {
            Request request = Request.get()
                    .setURL("https://fl-test.hbz-nrw.de")
                    .setVersion("HTTP/2.0")
                    .build()
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status()
                                + " response body = " + response);
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }
}
