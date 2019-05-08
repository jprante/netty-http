package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Request;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestBuilderTest {

    @Test
    void testSimpleRequest() {
        Request request = Request.builder(HttpMethod.GET)
                .content("Hello", "text/plain")
                .build();
        assertEquals("localhost", request.url().getHost());
        assertNull(request.url().getPort());
        assertEquals("Hello", request.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void testGetRequest() {
        Request request = Request.builder(HttpMethod.GET)
                .url("http://xbib.org")
                .addParameter("param1", "value1")
                .addParameter("param2", "value2")
                .build();
        assertEquals("?param1=value1&param2=value2", request.relativeUri());
        assertEquals("http://xbib.org/?param1=value1&param2=value2", request.url().toString());
    }

    @Test
    void testPostRequest() {
        Request request = Request.builder(HttpMethod.POST)
                .url("http://xbib.org")
                .addParameter("param1", "value1")
                .addParameter("param2", "value2")
                .content("Hello", "text/plain")
                .build();
        assertEquals("?param1=value1&param2=value2", request.relativeUri());
        assertEquals("http://xbib.org/?param1=value1&param2=value2", request.url().toString());
        assertEquals("Hello", request.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void testRequest() {
        Request request = Request.get()
                .url("https://google.com")
                .setVersion("HTTP/1.1")
                .build();
        assertEquals("google.com", request.url().getHost());

    }
}
