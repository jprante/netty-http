package org.xbib.netty.http.client.rest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.logging.Logger;

class RestClientTest {

    private static final Logger logger = Logger.getLogger(RestClientTest.class.getName());

    @Test
    void testSimpleGet() throws IOException {
        String result = RestClient.get("http://xbib.org").asString();
        logger.info(result);
    }
}
