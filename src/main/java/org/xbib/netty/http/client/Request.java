package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.Cookie;

import org.xbib.net.URL;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpPushListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 *
 */
public class Request {

    private final URL base;

    private final HttpVersion httpVersion;

    private final HttpMethod httpMethod;

    private final HttpHeaders headers;

    private final Collection<Cookie> cookies;

    private final String uri;

    private final ByteBuf content;

    private final int timeout;

    private final boolean followRedirect;

    private final int maxRedirects;

    private int redirectCount;

    private HttpResponseListener responseListener;

    private ExceptionListener exceptionListener;

    private HttpHeadersListener headersListener;

    private CookieListener cookieListener;

    private HttpPushListener pushListener;

    Request(URL url, HttpVersion httpVersion, HttpMethod httpMethod,
            HttpHeaders headers, Collection<Cookie> cookies,
            String uri, ByteBuf content,
            int timeout, boolean followRedirect, int maxRedirect, int redirectCount) {
        this.base = url;
        this.httpVersion = httpVersion;
        this.httpMethod = httpMethod;
        this.headers = headers;
        this.cookies = cookies;
        this.uri = uri;
        this.content = content;
        this.timeout = timeout;
        this.followRedirect = followRedirect;
        this.maxRedirects = maxRedirect;
        this.redirectCount = redirectCount;
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

    public int getTimeout() {
        return timeout;
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public boolean checkRedirect() {
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
        sb.append("base=").append(base).append(',')
                .append("version=").append(httpVersion).append(',')
                .append("method=").append(httpMethod).append(',')
                .append("relativeUri=").append(uri).append(',')
                .append("headers=").append(headers).append(',')
                .append("content=").append(content != null ? content.copy(0,16).toString(StandardCharsets.UTF_8) : "");
        return sb.toString();
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

    public Request setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public Request setPushListener(HttpPushListener httpPushListener) {
        this.pushListener = httpPushListener;
        return this;
    }

    public HttpPushListener getPushListener() {
        return pushListener;
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
}
