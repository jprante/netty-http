package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
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

    private Map<Cookie, Boolean> cookieBox;

    BaseTransport(Client client, HttpAddress httpAddress) {
        this.client = client;
        this.httpAddress = httpAddress;
        this.requests = new ConcurrentSkipListMap<>();
    }

    @Override
    public HttpAddress httpAddress() {
        return httpAddress;
    }

    @Override
    public Transport execute(Request request) throws IOException {
        ensureConnect();
        // some HTTP 1.1 servers like Elasticsearch do not understand full URIs in HTTP command line
        String uri = request.httpVersion().majorVersion() < 2 ?
                request.base().relativeReference() : request.base().toString();
        FullHttpRequest fullHttpRequest = request.content() == null ?
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri) :
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri,
                        request.content());
        logger.log(Level.INFO, fullHttpRequest.toString());
        Integer streamId = nextStream();
        if (streamId != null) {
            request.headers().add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), Integer.toString(streamId));
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
        requests.put(streamId, request);
        logger.log(Level.FINE, () -> "streamId = " + streamId + " writing request = " + fullHttpRequest);
        channel.writeAndFlush(fullHttpRequest);
        return this;
    }

    /**
     * Experimental.
     * @param request request
     * @param supplier supplier
     * @param <T> supplier result
     * @return completable future
     */
    @Override
    public <T> CompletableFuture<T> execute(Request request,
                                            Function<FullHttpResponse, T> supplier) throws IOException {
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        //request.setExceptionListener(completableFuture::completeExceptionally);
        request.setResponseListener(response -> completableFuture.complete(supplier.apply(response)));
        execute(request);
        return completableFuture;
    }

    @Override
    public synchronized void close() {
        get();
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    protected void ensureConnect() throws IOException {
        if (channel == null) {
            try {
                channel = client.newChannel(httpAddress);
                channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
                awaitSettings();
            } catch (InterruptedException e) {
                throw new ConnectException("unable to connect to " + httpAddress);
            }
        }
    }

    protected Request continuation(Integer streamId, FullHttpResponse httpResponse) throws URLSyntaxException {
        if (httpResponse == null) {
            return null;
        }
        Request request = fromStreamId(streamId);
        if (request == null) {
            // push promise
            return null;
        }
        try {
            if (request.checkRedirect()) {
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
                            logger.log(Level.INFO, "found redirect location: " + location);
                            URL redirUrl = URL.base(request.base()).resolve(location);
                            HttpMethod method = httpResponse.status().code() == 303 ? HttpMethod.GET : request.httpMethod();
                            RequestBuilder newHttpRequestBuilder = Request.builder(method)
                                    .url(redirUrl)
                                    .setVersion(request.httpVersion())
                                    .setHeaders(request.headers())
                                    .content(request.content());
                            // TODO(jprante) convencience to copy pathAndQuery from one request to another
                            request.base().getQueryParams().forEach(pair ->
                                newHttpRequestBuilder.addParameter(pair.getFirst(), pair.getSecond())
                            );
                            request.cookies().forEach(newHttpRequestBuilder::addCookie);
                            Request newHttpRequest = newHttpRequestBuilder.build();
                            newHttpRequest.setResponseListener(request.getResponseListener());
                            //newHttpRequest.setExceptionListener(request.getExceptionListener());
                            newHttpRequest.setHeadersListener(request.getHeadersListener());
                            newHttpRequest.setCookieListener(request.getCookieListener());
                            //newHttpRequest.setPushListener(request.getPushListener());
                            StringBuilder hostAndPort = new StringBuilder();
                            hostAndPort.append(redirUrl.getHost());
                            if (redirUrl.getPort() != null) {
                                hostAndPort.append(':').append(redirUrl.getPort());
                            }
                            newHttpRequest.headers().set(HttpHeaderNames.HOST, hostAndPort.toString());
                            logger.log(Level.INFO, "redirect url: " + redirUrl +
                                    " old request: " + request.toString() +
                                    " new request: " + newHttpRequest.toString());
                            return newHttpRequest;
                        }
                        break;
                    default:
                        logger.log(Level.FINE, "no redirect because of status code " + status);
                        break;
                }
            }
        } catch (MalformedInputException | UnmappableCharacterException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return null;
    }

    protected Request fromStreamId(Integer streamId) {
        if (streamId == null) {
            streamId = requests.lastKey();
        }
       return requests.get(streamId);
    }

    public void setCookieBox(Map<Cookie, Boolean> cookieBox) {
        this.cookieBox = cookieBox;
    }

    public Map<Cookie, Boolean> getCookieBox() {
        return cookieBox;
    }

    public void addCookie(Cookie cookie) {
        if (cookieBox == null) {
            this.cookieBox = Collections.synchronizedMap(new LRUCache<Cookie, Boolean>(32));
        }
        cookieBox.put(cookie, true);
    }

    private List<Cookie> matchCookiesFromBox(Request request) {
        return cookieBox == null ? Collections.emptyList() : cookieBox.keySet().stream().filter(cookie ->
                matchCookie(request.base(), cookie)
        ).collect(Collectors.toList());
    }

    private List<Cookie> matchCookies(Request request) {
        return request.cookies().stream().filter(cookie ->
                matchCookie(request.base(), cookie)
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

    class LRUCache<K, V> extends LinkedHashMap<K, V> {

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
