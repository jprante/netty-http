package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.listener.HttpPushListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class HttpClientRequestBuilder implements HttpRequestBuilder, HttpRequestDefaults {

    private static final Logger logger = Logger.getLogger(HttpClientRequestBuilder.class.getName());

    private final HttpClient httpClient;

    private final ByteBufAllocator byteBufAllocator;

    private final AtomicInteger streamId;

    private final DefaultHttpHeaders headers;

    private final List<String> removeHeaders;

    private final Set<Cookie> cookies;

    private final HttpMethod httpMethod;

    private int timeout = DEFAULT_TIMEOUT_MILLIS;

    private HttpVersion httpVersion = DEFAULT_HTTP_VERSION;

    private String userAgent = DEFAULT_USER_AGENT;

    private boolean gzip = DEFAULT_GZIP;

    private boolean followRedirect = DEFAULT_FOLLOW_REDIRECT;

    private int maxRedirects = DEFAULT_MAX_REDIRECT;

    private URI uri;

    private QueryStringEncoder queryStringEncoder;

    private ByteBuf content;

    private HttpRequest httpRequest;

    private HttpRequestContext httpRequestContext;

    private HttpResponseListener httpResponseListener;

    private ExceptionListener exceptionListener;

    private HttpHeadersListener httpHeadersListener;

    private CookieListener cookieListener;

    private HttpPushListener httpPushListener;

    /**
     * Construct HTTP client request builder.
     *
     * @param httpClient HTTP client
     * @param httpMethod HTTP method
     * @param byteBufAllocator byte buf allocator
     */
    HttpClientRequestBuilder(HttpClient httpClient, HttpMethod httpMethod,
                             ByteBufAllocator byteBufAllocator, int streamId) {
        this.httpClient = httpClient;
        this.httpMethod = httpMethod;
        this.byteBufAllocator = byteBufAllocator;
        this.streamId = new AtomicInteger(streamId);
        this.headers = new DefaultHttpHeaders();
        this.removeHeaders = new ArrayList<>();
        this.cookies = new HashSet<>();
    }

    @Override
    public HttpRequestBuilder setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public HttpRequestBuilder setURL(String url) {
        this.uri = URI.create(url);
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri, StandardCharsets.UTF_8);
        this.queryStringEncoder = new QueryStringEncoder(queryStringDecoder.path());
        for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
            for (String value : entry.getValue()) {
                queryStringEncoder.addParam(entry.getKey(), value);
            }
        }
        return this;
    }

    @Override
    public HttpRequestBuilder addHeader(String name, Object value) {
        headers.add(name, value);
        return this;
    }

    @Override
    public HttpRequestBuilder setHeader(String name, Object value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public HttpRequestBuilder removeHeader(String name) {
        removeHeaders.add(name);
        return this;
    }

    @Override
    public HttpRequestBuilder addParam(String name, String value) {
        if (queryStringEncoder != null) {
            queryStringEncoder.addParam(name, value);
        }
        return this;
    }

    @Override
    public HttpRequestBuilder addCookie(Cookie cookie) {
        cookies.add(cookie);
        return this;
    }

    @Override
    public HttpRequestBuilder contentType(String contentType) {
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
        return this;
    }

    @Override
    public HttpRequestBuilder setVersion(String httpVersion) {
        this.httpVersion = HttpVersion.valueOf(httpVersion);
        return this;
    }

    @Override
    public HttpRequestBuilder acceptGzip(boolean gzip) {
        this.gzip = gzip;
        return this;
    }

    @Override
    public HttpRequestBuilder setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect;
        return this;
    }

    @Override
    public HttpRequestBuilder setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    @Override
    public HttpRequestBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    @Override
    public HttpRequestBuilder text(String text) throws IOException {
        content(text, HttpHeaderValues.TEXT_PLAIN);
        return this;
    }

    @Override
    public HttpRequestBuilder json(String json) throws IOException {
        content(json, HttpHeaderValues.APPLICATION_JSON);
        return this;
    }

    @Override
    public HttpRequestBuilder xml(String xml) throws IOException {
        content(xml, "application/xml");
        return this;
    }

    @Override
    public HttpRequestBuilder content(CharSequence charSequence, String contentType) throws IOException {
        content(charSequence.toString().getBytes(CharsetUtil.UTF_8), AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequestBuilder content(byte[] buf, String contentType) throws IOException {
        content(buf, AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequestBuilder content(ByteBuf body, String contentType) throws IOException {
        content(body, AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequestBuilder onHeaders(HttpHeadersListener httpHeadersListener) {
        this.httpHeadersListener = httpHeadersListener;
        return this;
    }

    @Override
    public HttpRequestBuilder onCookie(CookieListener cookieListener) {
        this.cookieListener = cookieListener;
        return this;
    }

    @Override
    public HttpRequestBuilder onResponse(HttpResponseListener httpResponseListener) {
        this.httpResponseListener = httpResponseListener;
        return this;
    }

    @Override
    public HttpRequestBuilder onException(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    @Override
    public HttpRequestBuilder onPushReceived(HttpPushListener httpPushListener) {
        this.httpPushListener = httpPushListener;
        return this;
    }

    @Override
    public HttpRequest build() {
        if (uri == null) {
            throw new IllegalStateException("URL not set");
        }
        if (uri.getHost() == null) {
            throw new IllegalStateException("URL host not set: " + uri);
        }
        DefaultHttpRequest httpRequest = createHttpRequest();
        String scheme = uri.getScheme();
        StringBuilder sb = new StringBuilder(uri.getHost());
        int defaultPort = "http".equals(scheme) ? 80 : "https".equals(scheme) ? 443 : -1;
        if (defaultPort != -1 && uri.getPort() != -1 && defaultPort != uri.getPort()) {
            sb.append(":").append(uri.getPort());
        }
        if (httpVersion.majorVersion() == 2) {
            httpRequest.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme);
        }
        String host = sb.toString();
        httpRequest.headers().add(HttpHeaderNames.HOST, host);
        httpRequest.headers().add(HttpHeaderNames.DATE,
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("GMT"))));
        if (userAgent != null) {
            httpRequest.headers().add(HttpHeaderNames.USER_AGENT, userAgent);
        }
        if (gzip) {
            httpRequest.headers().add(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        }
        httpRequest.headers().setAll(headers);
        if (!httpRequest.headers().contains(HttpHeaderNames.ACCEPT)) {
            httpRequest.headers().add(HttpHeaderNames.ACCEPT, "*/*");
        }
        // RFC 2616 Section 14.10
        // "An HTTP/1.1 client that does not support persistent connections MUST include the "close" connection
        // option in every request message."
        if (httpVersion.majorVersion() == 1 && !httpRequest.headers().contains(HttpHeaderNames.CONNECTION)) {
            httpRequest.headers().add(HttpHeaderNames.CONNECTION, "close");
        }
        // forced removal of headers, at last
        for (String headerName : removeHeaders) {
            httpRequest.headers().remove(headerName);
        }
        return httpRequest;
    }

    @Override
    public HttpRequestContext execute() {
        if (httpRequest == null) {
            httpRequest = build();
        }
        if (httpResponseListener == null) {
            httpResponseListener = httpRequestContext;
        }
        httpRequestContext = new HttpRequestContext(uri, httpRequest, streamId,
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                timeout, System.currentTimeMillis(),
                followRedirect, maxRedirects, new AtomicInteger(0),
                new CountDownLatch(1),
                httpResponseListener,
                exceptionListener,
                httpHeadersListener,
                cookieListener,
                httpPushListener);
        // copy cookie(s) to context, will be added later to headers in dispatch (because of auto-cookie setting while redirect)
        if (!cookies.isEmpty()) {
            for (Cookie cookie : cookies) {
                httpRequestContext.addCookie(cookie);
            }
        }
        httpClient.dispatch(httpRequestContext);
        return httpRequestContext;
    }

    @Override
    public <T> CompletableFuture<T> execute(Function<FullHttpResponse, T> supplier) {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        onResponse(response -> completableFuture.complete(supplier.apply(response)));
        onException(completableFuture::completeExceptionally);
        execute();
        return completableFuture;
    }

    private DefaultHttpRequest createHttpRequest() {
        String requestTarget = toOriginForm();
        logger.log(Level.FINE, () -> "origin form is " + requestTarget);
        return content == null ?
                new DefaultHttpRequest(httpVersion, httpMethod, requestTarget) :
                new DefaultFullHttpRequest(httpVersion, httpMethod, requestTarget, content);
    }

    private String toOriginForm() {
        StringBuilder sb = new StringBuilder();
        String pathAndQuery = queryStringEncoder.toString();
        sb.append(pathAndQuery.isEmpty() ? "/" : pathAndQuery);
        String ref = uri.getFragment();
        if (ref != null && !ref.isEmpty()) {
            sb.append('#').append(ref);
        }
        return sb.toString();
    }

    private void addHeader(AsciiString name, Object value) {
        headers.add(name, value);
    }

    private void content(CharSequence charSequence, AsciiString contentType) throws IOException {
        content(charSequence.toString().getBytes(CharsetUtil.UTF_8), contentType);
    }

    private void content(byte[] buf, AsciiString contentType) throws IOException {
        ByteBuf buffer = byteBufAllocator.buffer(buf.length).writeBytes(buf);
        content(buffer, contentType);
    }

    private void content(ByteBuf body, AsciiString contentType) throws IOException {
        this.content = body;
        addHeader(HttpHeaderNames.CONTENT_LENGTH, (long) body.readableBytes());
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
    }
}
