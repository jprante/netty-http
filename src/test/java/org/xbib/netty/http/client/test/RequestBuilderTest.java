package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;
import org.xbib.netty.http.client.Request;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestBuilderTest {

    private static final Logger logger = Logger.getLogger(RequestBuilderTest.class.getName());

    @Test
    public void testSimpleRequest() {
        Request request = Request.builder(HttpMethod.GET).content("Hello", "text/plain").build();
        logger.log(Level.INFO, request.toString());
    }
}
