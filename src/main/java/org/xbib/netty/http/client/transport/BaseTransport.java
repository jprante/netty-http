package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.net.PercentDecoder;
import org.xbib.net.URL;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.RequestBuilder;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.retry.BackOff;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

abstract class BaseTransport implements Transport {

    private static final Logger logger = Logger.getLogger(BaseTransport.class.getName());

    protected final Client client;

    protected final HttpAddress httpAddress;

    protected Channel channel;

    protected SortedMap<Integer, Request> requests;

    protected Throwable throwable;

    private Map<Cookie, Boolean> cookieBox;

    BaseTransport(Client client, HttpAddress httpAddress) {
        this.client = client;
        this.httpAddress = httpAddress;
        this.requests = new ConcurrentSkipListMap<>();
    }

    @Override
    public Transport execute(Request request) throws IOException {
        ensureConnect();
        if (throwable != null) {
            return this;
        }
        // Some HTTP 1 servers do not understand URIs in HTTP command line in spite of RFC 7230.
        // The "origin form" requires a "Host" header.
        // Our algorithm is: use always "origin form" for HTTP 1, use absolute form for HTTP 2.
        // The reason is that Netty derives the HTTP/2 scheme header from the absolute form.
        String uri = request.httpVersion().majorVersion() == 1 ?
                request.url().relativeReference() : request.url().toString();
        FullHttpRequest fullHttpRequest = request.content() == null ?
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri) :
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri,
                        request.content());
        Integer streamId = nextStream();
        if (streamId != null && streamId > 0) {
            request.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), Integer.toString(streamId));
        } else {
            if (request.httpVersion().majorVersion() == 2) {
                logger.log(Level.WARNING, "no streamId but HTTP/2 request. Strange!!! " + getClass().getName());
            }
        }
        // add matching cookies from box (previous requests) and new cookies from request builder
        Collection<Cookie> cookies = new ArrayList<>();
        cookies.addAll(matchCookiesFromBox(request));
        cookies.addAll(matchCookies(request));
        if (!cookies.isEmpty()) {
            request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(cookies));
        }
        // add stream-id and cookie headers
        fullHttpRequest.headers().set(request.headers());
        if (streamId != null) {
            requests.put(streamId, request);
        }
        if (channel.isWritable()) {
            channel.writeAndFlush(fullHttpRequest);

        }
        return this;
    }

    /**
     * Experimental method for executing in a wrapping completable future.
     * @param request request
     * @param supplier supplier
     * @param <T> supplier result
     * @return completable future
     */
    @Override
    public <T> CompletableFuture<T> execute(Request request,
                                            Function<FullHttpResponse, T> supplier) throws IOException {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        request.setResponseListener(response -> completableFuture.complete(supplier.apply(response)));
        execute(request);
        return completableFuture;
    }

    @Override
    public synchronized void close() throws IOException {
        get();
        client.releaseChannel(channel);
    }

    @Override
    public boolean isFailed() {
        return throwable != null;
    }

    @Override
    public Throwable getFailure() {
        return throwable;
    }

    @Override
    public void headersReceived(Integer streamId, HttpHeaders httpHeaders) {
        Request request =  fromStreamId(streamId);
        if (request != null) {
            HttpHeadersListener httpHeadersListener = request.getHeadersListener();
            if (httpHeadersListener != null) {
                httpHeadersListener.onHeaders(httpHeaders);
            }
            for (String cookieString : httpHeaders.getAll(HttpHeaderNames.SET_COOKIE)) {
                Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
                addCookie(cookie);
                CookieListener cookieListener = request.getCookieListener();
                if (cookieListener != null) {
                    cookieListener.onCookie(cookie);
                }
            }
        }
    }

    private void ensureConnect() throws IOException {
        if (channel == null) {
            channel = client.newChannel(httpAddress);
            if (channel != null) {
                channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
                awaitSettings();
            } else {
                ConnectException connectException;
                if (httpAddress != null) {
                    connectException = new ConnectException("unable to connect to " + httpAddress);
                } else if (client.hasPooledConnections()){
                    connectException = new ConnectException("unable to get channel from pool");
                } else {
                    // if API misuse
                    connectException = new ConnectException("unable to get channel");
                }
                this.throwable = connectException;
                this.channel = null;
                throw connectException;
            }
        }
    }

    protected Request fromStreamId(Integer streamId) {
        if (streamId == null) {
            streamId = requests.lastKey();
        }
        return requests.get(streamId);
    }

    protected Request continuation(Request request, FullHttpResponse httpResponse) throws URLSyntaxException {
        if (httpResponse == null) {
            return null;
        }
        if (request == null) {
            // push promise or something else
            return null;
        }
        try {
            if (request.canRedirect()) {
                int status = httpResponse.status().code();
                switch (status) {
                    case 300:
                    case 301:
                    case 302:
                    case 303:
                    case 305:
                    case 307:
                    case 308:
                        String location = httpResponse.headers().get(HttpHeaderNames.LOCATION);
                        location = new PercentDecoder(StandardCharsets.UTF_8.newDecoder()).decode(location);
                        if (location != null) {
                            logger.log(Level.FINE, "found redirect location: " + location);
                            URL redirUrl = URL.base(request.url()).resolve(location);
                            HttpMethod method = httpResponse.status().code() == 303 ? HttpMethod.GET : request.httpMethod();
                            RequestBuilder newHttpRequestBuilder = Request.builder(method)
                                    .url(redirUrl)
                                    .setVersion(request.httpVersion())
                                    .setHeaders(request.headers())
                                    .content(request.content());
                            request.url().getQueryParams().forEach(pair ->
                                newHttpRequestBuilder.addParameter(pair.getFirst(), pair.getSecond())
                            );
                            request.cookies().forEach(newHttpRequestBuilder::addCookie);
                            Request newHttpRequest = newHttpRequestBuilder.build();
                            newHttpRequest.setResponseListener(request.getResponseListener());
                            newHttpRequest.setHeadersListener(request.getHeadersListener());
                            newHttpRequest.setCookieListener(request.getCookieListener());
                            StringBuilder hostAndPort = new StringBuilder();
                            hostAndPort.append(redirUrl.getHost());
                            if (redirUrl.getPort() != null) {
                                hostAndPort.append(':').append(redirUrl.getPort());
                            }
                            newHttpRequest.headers().set(HttpHeaderNames.HOST, hostAndPort.toString());
                            logger.log(Level.FINE, "redirect url: " + redirUrl +
                                    " old request: " + request.toString() +
                                    " new request: " + newHttpRequest.toString());
                            return newHttpRequest;
                        }
                        break;
                    default:
                        break;
                }
            }
        } catch (MalformedInputException | UnmappableCharacterException e) {
            this.throwable = e;
        }
        return null;
    }

    protected Request retry(Request request, FullHttpResponse httpResponse) {
        if (httpResponse == null) {
            return null;
        }
        if (request == null) {
            // push promise or something else
            return null;
        }
        if (request.isBackOff()) {
            BackOff backOff = request.getBackOff() != null ? request.getBackOff() :
                    client.getClientConfig().getBackOff();
            int status = httpResponse.status().code();
            switch (status) {
                case 403:
                case 404:
                case 500:
                case 502:
                case 503:
                case 504:
                case 507:
                case 509:
                    if (backOff != null) {
                        long millis = backOff.nextBackOffMillis();
                        if (millis != BackOff.STOP) {
                            logger.log(Level.FINE, "status = " + status + " backing off request by " + millis + " milliseconds");
                            try {
                                Thread.sleep(millis);
                            } catch (InterruptedException e) {
                                // ignore
                            }
                            return request;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        return null;
    }

    public void setCookieBox(Map<Cookie, Boolean> cookieBox) {
        this.cookieBox = cookieBox;
    }

    public Map<Cookie, Boolean> getCookieBox() {
        return cookieBox;
    }

    private void addCookie(Cookie cookie) {
        if (cookieBox == null) {
            this.cookieBox = Collections.synchronizedMap(new LRUCache<Cookie, Boolean>(32));
        }
        cookieBox.put(cookie, true);
    }

    private List<Cookie> matchCookiesFromBox(Request request) {
        return cookieBox == null ? Collections.emptyList() : cookieBox.keySet().stream().filter(cookie ->
                matchCookie(request.url(), cookie)
        ).collect(Collectors.toList());
    }

    private List<Cookie> matchCookies(Request request) {
        return request.cookies().stream().filter(cookie ->
                matchCookie(request.url(), cookie)
        ).collect(Collectors.toList());
    }

    private boolean matchCookie(URL url, Cookie cookie) {
        boolean domainMatch = cookie.domain() == null || url.getHost().endsWith(cookie.domain());
        if (!domainMatch) {
            return false;
        }
        boolean pathMatch = "/".equals(cookie.path()) || url.getPath().startsWith(cookie.path());
        if (!pathMatch) {
            return false;
        }
        boolean secureScheme = "https".equals(url.getScheme());
        return (secureScheme && cookie.isSecure()) || (!secureScheme && !cookie.isSecure());
    }

    @SuppressWarnings("serial")
    static class LRUCache<K, V> extends LinkedHashMap<K, V> {

        private final int cacheSize;

        LRUCache(int cacheSize) {
            super(16, 0.75f, true);
            this.cacheSize = cacheSize;
        }

        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() >= cacheSize;
        }
    }
}
