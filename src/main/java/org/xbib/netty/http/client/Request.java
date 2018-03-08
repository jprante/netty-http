package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;

import org.xbib.net.URL;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;
import org.xbib.netty.http.client.retry.BackOff;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client request.
 */
public class Request implements Closeable {

    private final URL base;

    private final HttpVersion httpVersion;

    private final HttpMethod httpMethod;

    private final HttpHeaders headers;

    private final Collection<Cookie> cookies;

    private final String uri;

    private final ByteBuf content;

    private final long timeoutInMillis;

    private final boolean followRedirect;

    private final int maxRedirects;

    private int redirectCount;

    private final boolean isBackOff;

    private final BackOff backOff;

    private CompletableFuture<?> completableFuture;

    private HttpResponseListener responseListener;

    private HttpHeadersListener headersListener;

    private CookieListener cookieListener;

    Request(URL url, HttpVersion httpVersion, HttpMethod httpMethod,
            HttpHeaders headers, Collection<Cookie> cookies,
            String uri, ByteBuf content,
            long timeoutInMillis, boolean followRedirect, int maxRedirect, int redirectCount,
            boolean isBackOff, BackOff backOff) {
        this.base = url;
        this.httpVersion = httpVersion;
        this.httpMethod = httpMethod;
        this.headers = headers;
        this.cookies = cookies;
        this.uri = uri;
        this.content = content;
        this.timeoutInMillis = timeoutInMillis;
        this.followRedirect = followRedirect;
        this.maxRedirects = maxRedirect;
        this.redirectCount = redirectCount;
        this.isBackOff = isBackOff;
        this.backOff = backOff;
    }

    public URL base() {
        return base;
    }

    public HttpVersion httpVersion() {
        return httpVersion;
    }

    public HttpMethod httpMethod() {
        return httpMethod;
    }

    public String relativeUri() {
        return uri;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public Collection<Cookie> cookies() {
        return cookies;
    }

    public ByteBuf content() {
        return content;
    }

    /**
     * Return the timeout in milliseconds per request. This overrides the read timeout of the client.
     * @return timeout timeout in milliseconds
     */
    public long getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public boolean isBackOff() {
        return isBackOff;
    }

    public BackOff getBackOff() {
        return backOff;
    }

    public boolean canRedirect() {
        if (!followRedirect) {
            return false;
        }
        if (redirectCount >= maxRedirects) {
            return false;
        }
        redirectCount = redirectCount + 1;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Request[base='").append(base)
                .append("',version=").append(httpVersion)
                .append(",method=").append(httpMethod)
                .append(",uri=").append(uri)
                .append(",headers=").append(headers.entries())
                .append(",content=").append(content != null ? content.copy(0,16).toString(StandardCharsets.UTF_8) : "")
                .append("]");
        return sb.toString();
    }

    public Request setCompletableFuture(CompletableFuture<?> completableFuture) {
        this.completableFuture = completableFuture;
        return this;
    }

    public CompletableFuture<?> getCompletableFuture() {
        return completableFuture;
    }

    public Request setHeadersListener(HttpHeadersListener httpHeadersListener) {
        this.headersListener = httpHeadersListener;
        return this;
    }

    public HttpHeadersListener getHeadersListener() {
        return headersListener;
    }

    public Request setCookieListener(CookieListener cookieListener) {
        this.cookieListener = cookieListener;
        return this;
    }

    public CookieListener getCookieListener() {
        return cookieListener;
    }

    public Request setResponseListener(HttpResponseListener httpResponseListener) {
        this.responseListener = httpResponseListener;
        return this;
    }

    public HttpResponseListener getResponseListener() {
        return responseListener;
    }

    public static RequestBuilder get() {
        return builder(HttpMethod.GET);
    }

    public static RequestBuilder put() {
        return builder(HttpMethod.PUT);
    }

    public static RequestBuilder post() {
        return builder(HttpMethod.POST);
    }

    public static RequestBuilder delete() {
        return builder(HttpMethod.DELETE);
    }

    public static RequestBuilder head() {
        return builder(HttpMethod.HEAD);
    }

    public static RequestBuilder patch() {
        return builder(HttpMethod.PATCH);
    }

    public static RequestBuilder trace() {
        return builder(HttpMethod.TRACE);
    }

    public static RequestBuilder options() {
        return builder(HttpMethod.OPTIONS);
    }

    public static RequestBuilder connect() {
        return builder(HttpMethod.CONNECT);
    }

    public static RequestBuilder builder(HttpMethod httpMethod) {
        return new RequestBuilder().setMethod(httpMethod);
    }

    @Override
    public void close() throws IOException {
        if (content != null) {
            content.release();
        }
    }
}
