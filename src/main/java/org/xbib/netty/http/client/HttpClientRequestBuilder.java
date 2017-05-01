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
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 *
 */
public class HttpClientRequestBuilder implements HttpRequestBuilder, HttpRequestDefaults {

    private static final AtomicInteger streamId = new AtomicInteger(3);

    private final HttpClient httpClient;

    private final ByteBufAllocator byteBufAllocator;

    private final DefaultHttpHeaders headers;

    private final List<String> removeHeaders;

    private final HttpMethod httpMethod;

    private int timeout = DEFAULT_TIMEOUT_MILLIS;

    private HttpVersion httpVersion = DEFAULT_HTTP_VERSION;

    private String userAgent = DEFAULT_USER_AGENT;

    private boolean gzip = DEFAULT_GZIP;

    private boolean followRedirect = DEFAULT_FOLLOW_REDIRECT;

    private int maxRedirects = DEFAULT_MAX_REDIRECT;

    private URL url;

    private ByteBuf body;

    private HttpRequest httpRequest;

    private HttpRequestContext httpRequestContext;

    private HttpResponseListener httpResponseListener;

    private ExceptionListener exceptionListener;

    /**
     * Construct HTTP client request builder.
     *
     * @param httpClient HTTP client
     * @param httpMethod HTTP method
     * @param byteBufAllocator byte buf allocator
     */
    HttpClientRequestBuilder(HttpClient httpClient, HttpMethod httpMethod, ByteBufAllocator byteBufAllocator) {
        this.httpClient = httpClient;
        this.httpMethod = httpMethod;
        this.byteBufAllocator = byteBufAllocator;
        this.headers = new DefaultHttpHeaders();
        this.removeHeaders = new ArrayList<>();
    }

    @Override
    public HttpRequestBuilder setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    protected int getTimeout() {
        return timeout;
    }

    @Override
    public HttpRequestBuilder setURL(String url) {
        try {
            this.url = new URL(url);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    protected URL getURL() {
        return url;
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
    public HttpRequestBuilder contentType(String contentType) {
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
        return this;
    }

    @Override
    public HttpRequestBuilder setVersion(String httpVersion) {
        this.httpVersion = HttpVersion.valueOf(httpVersion);
        return this;
    }

    protected HttpVersion getVersion() {
        return httpVersion;
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

    protected boolean isFollowRedirect() {
        return followRedirect;
    }

    @Override
    public HttpRequestBuilder setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    protected int getMaxRedirects() {
        return maxRedirects;
    }

    @Override
    public HttpRequestBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    @Override
    public HttpRequestBuilder text(String text) throws IOException {
        setBody(text, HttpHeaderValues.TEXT_PLAIN);
        return this;
    }

    @Override
    public HttpRequestBuilder json(String json) throws IOException {
        setBody(json, HttpHeaderValues.APPLICATION_JSON);
        return this;
    }

    @Override
    public HttpRequestBuilder xml(String xml) throws IOException {
        setBody(xml, "application/xml");
        return this;
    }

    @Override
    public HttpRequestBuilder setBody(CharSequence charSequence, String contentType) throws IOException {
        setBody(charSequence.toString().getBytes(CharsetUtil.UTF_8), AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequestBuilder setBody(byte[] buf, String contentType) throws IOException {
        setBody(buf, AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequestBuilder setBody(ByteBuf body, String contentType) throws IOException {
        setBody(body, AsciiString.of(contentType));
        return this;
    }

    @Override
    public HttpRequest build() {
        if (url == null) {
            throw new IllegalStateException("URL not set");
        }
        if (url.getHost() == null) {
            throw new IllegalStateException("URL host not set: " + url);
        }
        DefaultHttpRequest httpRequest = createHttpRequest();
        String scheme = url.getProtocol();
        StringBuilder sb = new StringBuilder(url.getHost());
        int defaultPort = "http".equals(scheme) ? 80 : "https".equals(scheme) ? 443 : -1;
        if (defaultPort != -1 && url.getPort() != -1 && defaultPort != url.getPort()) {
            sb.append(":").append(url.getPort());
        }
        if (httpVersion.majorVersion() == 2) {
            // this is a hack, because we only use the "origin-form" in request URIs
            httpRequest.headers().set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme);
        }
        httpRequest.headers().add(HttpHeaderNames.HOST, sb.toString());
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

    private DefaultHttpRequest createHttpRequest() {
        // Regarding request-target URI:
        // RFC https://tools.ietf.org/html/rfc7230#section-5.3.2
        // would allow url.toExternalForm as absolute-form,
        // but some servers do not support that. So, we create origin-form.
        // But for HTTP/2, we should create the absolute-form, otherwise
        // netty will throw "java.lang.IllegalArgumentException: :scheme must be specified."
        String requestTarget = toOriginForm(url);
        return body == null ?
                new DefaultHttpRequest(httpVersion, httpMethod, requestTarget) :
                new DefaultFullHttpRequest(httpVersion, httpMethod, requestTarget, body);
    }

    private String toOriginForm(URL base) {
        StringBuilder sb = new StringBuilder();
        String path = base.getPath() != null && !base.getPath().isEmpty() ? base.getPath() : "/";
        String query = base.getQuery();
        String ref = base.getRef();
        if (path.charAt(0) != '/') {
            sb.append('/');
        }
        sb.append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?').append(query);
        }
        if (ref != null && !ref.isEmpty()) {
            sb.append('#').append(ref);
        }
        return sb.toString();
    }

    private void addHeader(AsciiString name, Object value) {
        headers.add(name, value);
    }

    private void setBody(CharSequence charSequence, AsciiString contentType) throws IOException {
        setBody(charSequence.toString().getBytes(CharsetUtil.UTF_8), contentType);
    }

    private void setBody(byte[] buf, AsciiString contentType) throws IOException {
        ByteBuf buffer = byteBufAllocator.buffer(buf.length).writeBytes(buf);
        setBody(buffer, contentType);
    }

    private void setBody(ByteBuf body, AsciiString contentType) throws IOException {
        this.body = body;
        addHeader(HttpHeaderNames.CONTENT_LENGTH, (long) body.readableBytes());
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
    }

    @Override
    public HttpRequestBuilder onResponse(HttpResponseListener httpResponseListener) {
        this.httpResponseListener = httpResponseListener;
        return this;
    }

    @Override
    public HttpRequestBuilder onError(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
        return this;
    }

    @Override
    public HttpRequestContext execute() {
        if (httpRequest == null) {
            httpRequest = build();
        }
        if (httpRequestContext == null) {
            httpRequestContext = new HttpRequestContext(getURL(),
                    httpRequest,
                    new AtomicBoolean(false),
                    new AtomicBoolean(false),
                    getTimeout(), System.currentTimeMillis(),
                    isFollowRedirect(), getMaxRedirects(), new AtomicInteger(0),
                    new CountDownLatch(1), streamId.get());
        }
        if (httpResponseListener == null) {
            httpResponseListener = httpRequestContext;
        }
        httpClient.dispatch(httpRequestContext, httpResponseListener, exceptionListener);
        return httpRequestContext;
    }

    @Override
    public <T> CompletableFuture<T> execute(Function<FullHttpResponse, T> supplier) {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        onResponse(response -> completableFuture.complete(supplier.apply(response)));
        onError(completableFuture::completeExceptionally);
        execute();
        return completableFuture;
    }
}
