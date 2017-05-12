package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;
import org.xbib.netty.http.client.HttpClientRequestBuilder;
import org.xbib.netty.http.client.HttpRequestBuilder;

import java.net.URI;

import static org.junit.Assert.assertEquals;

/**
 */
public class URITest {

    @Test
    public void testURIResolve() {
        URI uri = URI.create("http://localhost");
        URI uri2 = uri.resolve("/path");
        assertEquals("http://localhost/path", uri2.toString());
        uri = URI.create("http://localhost/path1?a=b");
        uri2 = uri.resolve("path2?c=d");
        assertEquals("http://localhost/path2?c=d", uri2.toString());
    }

    @Test
    public void testClientRequestURIs() {
        HttpRequestBuilder httpRequestBuilder = HttpClientRequestBuilder.builder(HttpMethod.GET);
        httpRequestBuilder.setURL("https://localhost").path("/path");
        assertEquals("/path", httpRequestBuilder.build().uri());
        httpRequestBuilder.path("/foobar");
        assertEquals("/foobar", httpRequestBuilder.build().uri());
        httpRequestBuilder.path("/path1?a=b");
        assertEquals("/path1?a=b", httpRequestBuilder.build().uri());
        httpRequestBuilder.path("/path2?c=d");
        assertEquals("/path2?c=d", httpRequestBuilder.build().uri());
    }
}
