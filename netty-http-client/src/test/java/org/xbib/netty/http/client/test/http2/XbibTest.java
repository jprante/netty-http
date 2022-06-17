package org.xbib.netty.http.client.test.http2;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;

public class XbibTest {

    private static final Logger logger = Logger.getLogger(XbibTest.class.getName());

    @Test
    void testXbib() throws Exception {
        try (Client client = Client.builder()
                .enableDebug()
                .build()) {
            Request request = Request.get()
                    .url("https://xbib.org/")
                    .setVersion("HTTP/2.0")
                    .setResponseListener(resp -> logger.log(Level.INFO, "got HTTP/2 response: " +
                            resp.getHeaders() + resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request).get().close();
        }
    }

}
