package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.xbib.net.URL;
import org.xbib.netty.http.client.api.Request;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestBuilderTest {

    @Test
    void testResolve() {
        URI uri = URI.create("http://localhost");
        URI uri2 = uri.resolve("/path");
        assertEquals("http://localhost/path", uri2.toString());
        uri = URI.create("http://localhost/path1?a=b");
        uri2 = uri.resolve("path2?c=d");
        assertEquals("http://localhost/path2?c=d", uri2.toString());
        URL url = URL.from("http://localhost");
        URL url2 = url.resolve("/path");
        assertEquals("http://localhost/path", url2.toString());
        url = URL.from("http://localhost/path1?a=b");
        url2 = url.resolve("path2?c=d");
        assertEquals("http://localhost/path2?c=d", url2.toString());
    }

    @Test
    void testRelativeUri() {
        Request.Builder httpRequestBuilder = Request.get();
        httpRequestBuilder.url("https://localhost/path");
        assertEquals("/path", httpRequestBuilder.build().relative());
        httpRequestBuilder.url("https://localhost/foobar");
        assertEquals("/foobar", httpRequestBuilder.build().relative());
        httpRequestBuilder.url("/path1?a=b");
        assertEquals("/path1?a=b", httpRequestBuilder.build().relative());
        httpRequestBuilder.url("/path2?c=d");
        assertEquals("/path2?c=d", httpRequestBuilder.build().relative());
    }

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
        assertEquals("?param1=value1&param2=value2", request.relative());
        assertEquals("http://xbib.org?param1=value1&param2=value2", request.url().toString());
    }

    @Test
    void testGetRequestWithPercent() {
        Request request = Request.builder(HttpMethod.GET)
                .url("http://xbib.org")
                .addParameter("param1", "value1")
                .addParameter("param2", "value%")
                .build();
        assertEquals("?param1=value1&param2=value%25", request.relative());
        assertEquals("http://xbib.org?param1=value1&param2=value%25", request.url().toExternalForm());
        assertEquals("http://xbib.org?param1=value1&param2=value%", request.url().toString());
    }

    @Test
    void testGetRequestWithSpaces() {
        Request request = Request.builder(HttpMethod.GET)
                .url("http://xbib.org")
                .addParameter(" param1 ", " value1 ")
                .addParameter(" param2 ", " value2 ")
                .build();
        assertEquals("?%20param1%20=%20value1%20&%20param2%20=%20value2%20", request.relative());
        assertEquals("http://xbib.org?%20param1%20=%20value1%20&%20param2%20=%20value2%20", request.url().toExternalForm());
    }

    @Test
    void testBasicPostRequest() {
        Request request = Request.builder(HttpMethod.POST)
                .url("http://xbib.org")
                .addParameter("param1", "value1")
                .addParameter("param2", "value2")
                .content("a=b&c=d", "application/x-www-form-urlencoded")
                .build();
        assertEquals("xbib.org", request.url().getHost());
        assertEquals("?param1=value1&param2=value2", request.relative());
        assertEquals("http://xbib.org?param1=value1&param2=value2", request.url().toExternalForm());
        assertEquals("a=b&c=d", request.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void testFormRequest() {
        Request request = Request.builder(HttpMethod.POST)
                .url("http://xbib.org")
                .addParameter("param1", "value1")
                .addParameter("param2", "value%")
                .content("a=b&c=%", "application/x-www-form-urlencoded")
                .build();
        assertEquals("xbib.org", request.url().getHost());
        assertEquals("?param1=value1&param2=value%25", request.relative());
        assertEquals("http://xbib.org?param1=value1&param2=value%25", request.url().toExternalForm());
        assertEquals("a=b&c=%", request.content().toString(StandardCharsets.UTF_8));
    }

    @Test
    void testRequest() {
        Request request = Request.get()
                .url("https://google.com")
                .build();
        assertEquals("google.com", request.url().getHost());
    }

    @Test
    void testRequestWithSpaceInParameters() {
        Request request = Request.get()
                .url("https://google.com? a = b")
                .build();
        assertEquals("google.com", request.url().getHost());
        assertEquals("https://google.com?%20a%20=%20b", request.absolute());
        assertEquals("?%20a%20=%20b", request.relative());
        assertEquals("https://google.com? a = b", request.url().toString());
        assertEquals("https://google.com?%20a%20=%20b", request.url().toExternalForm());
        request = Request.get()
                .url("https://google.com?%20a%20=%20b")
                .build();
        assertEquals("google.com", request.url().getHost());
        assertEquals("https://google.com?%20a%20=%20b", request.absolute());
        assertEquals("?%20a%20=%20b", request.relative());
        assertEquals("https://google.com? a = b", request.url().toString());
        assertEquals("https://google.com?%20a%20=%20b", request.url().toExternalForm());
    }

    @Test
    void testMassiveQueryParameters() {
        Request.Builder requestBuilder = Request.builder(HttpMethod.GET);
        for (int i = 0; i < 2000; i++) {
            requestBuilder.addParameter("param" + i, "value" + i);
        }
        Request request = requestBuilder.build();
        assertEquals(18276, request.absolute().length());
    }
}
