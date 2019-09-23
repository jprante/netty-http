package org.xbib.netty.http.client.test.http2;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GoogleTest {

    private static final Logger logger = Logger.getLogger(GoogleTest.class.getName());

    @Test
    void testSequentialRequests() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            // TODO decompression of frames
            Request request2 = Request.get().url("https://google.com").setVersion("HTTP/2.0")
                    .setResponseListener(resp -> logger.log(Level.INFO, "got HTTP/2 response: " +
                            resp.getHeaders() + resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request2).get();
        } finally {
            client.shutdownGracefully();
        }
    }

}
