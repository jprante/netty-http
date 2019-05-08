package org.xbib.netty.http.client.test;

import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.RequestBuilder;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class URITest {

    @Test
    void testURIResolve() {
        URI uri = URI.create("http://localhost");
        URI uri2 = uri.resolve("/path");
        assertEquals("http://localhost/path", uri2.toString());
        uri = URI.create("http://localhost/path1?a=b");
        uri2 = uri.resolve("path2?c=d");
        assertEquals("http://localhost/path2?c=d", uri2.toString());
    }

    @Test
    void testRelativeUri() {
        RequestBuilder httpRequestBuilder = Request.get();
        httpRequestBuilder.url("https://localhost").uri("/path");
        assertEquals("/path", httpRequestBuilder.build().relativeUri());
        httpRequestBuilder.uri("/foobar");
        assertEquals("/foobar", httpRequestBuilder.build().relativeUri());
        httpRequestBuilder.uri("/path1?a=b");
        assertEquals("/path1?a=b", httpRequestBuilder.build().relativeUri());
        httpRequestBuilder.uri("/path2?c=d");
        assertEquals("/path2?c=d", httpRequestBuilder.build().relativeUri());
    }
}
