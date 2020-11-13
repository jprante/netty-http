package org.xbib.netty.http.client.rest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class RestClientTest {

    private static final Logger logger = Logger.getLogger(RestClientTest.class.getName());

    @Test
    void testSimpleGet() throws IOException {
        String result = RestClient.get("https://xbib.org").asString();
        logger.info(result);
    }
}
