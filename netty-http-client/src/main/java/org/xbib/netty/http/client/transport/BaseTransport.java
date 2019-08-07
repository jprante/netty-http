package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.SslHandler;
import org.xbib.net.PercentDecoder;
import org.xbib.net.URL;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.retry.BackOff;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.common.cookie.CookieBox;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

abstract class BaseTransport implements Transport {

    private static final Logger logger = Logger.getLogger(BaseTransport.class.getName());

    protected final Client client;

    protected final HttpAddress httpAddress;

    protected Throwable throwable;

    private static final Request DUMMY = Request.builder(HttpMethod.GET).build();

    private final Map<Request, Channel> channels;

    private SSLSession sslSession;

    final Map<String, Flow> channelFlowMap;

    final SortedMap<String, Request> requests;

    private CookieBox cookieBox;

    BaseTransport(Client client, HttpAddress httpAddress) {
        this.client = client;
        this.httpAddress = httpAddress;
        this.channels = new ConcurrentHashMap<>();
        this.channelFlowMap = new ConcurrentHashMap<>();
        this.requests = new ConcurrentSkipListMap<>();
    }

    @Override
    public HttpAddress getHttpAddress() {
        return httpAddress;
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
                                            Function<HttpResponse, T> supplier) throws IOException {
        Objects.requireNonNull(supplier);
        final CompletableFuture<T> completableFuture = new CompletableFuture<>();
        request.setResponseListener(response -> {
            if (response != null) {
                completableFuture.complete(supplier.apply(response));
            } else {
                completableFuture.cancel(true);
            }
        });
        execute(request);
        return completableFuture;
    }

    @Override
    public synchronized void close() {
        get();
    }

    @Override
    public boolean isFailed() {
        return throwable != null;
    }

    @Override
    public Throwable getFailure() {
        return throwable;
    }

    /**
     * The underlying network layer failed, not possible to know the request.
     * So we fail all (open) promises.
     * @param throwable the exception
     */
    @Override
    public void fail(Throwable throwable) {
        // do not fail more than once
        if (this.throwable != null) {
            return;
        }
        logger.log(Level.SEVERE, "failing: " + throwable.getMessage(), throwable);
        this.throwable = throwable;
        for (Flow flow : channelFlowMap.values()) {
            flow.fail(throwable);
        }
    }

    @Override
    public Transport get() {
        return get(client.getClientConfig().getReadTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public Transport get(long value, TimeUnit timeUnit) {
        for (Map.Entry<String, Flow> entry : channelFlowMap.entrySet()) {
            Flow flow = entry.getValue();
            for (Integer key : flow.keys()) {
                try {
                    flow.get(key).get(value, timeUnit);
                } catch (Exception e) {
                    String requestKey = getRequestKey(entry.getKey(), key);
                    if (requestKey != null) {
                        Request request = requests.get(requestKey);
                        if (request != null && request.getCompletableFuture() != null) {
                            request.getCompletableFuture().completeExceptionally(e);
                        }
                    }
                    flow.fail(e);
                } finally {
                    flow.remove(key);
                }
            }
            flow.close();
        }
        channels.values().forEach(channel -> {
            try {
                client.releaseChannel(channel, true);
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        });
        channelFlowMap.clear();
        channels.clear();
        requests.clear();
        return this;
    }

    @Override
    public void cancel() {
        for (Map.Entry<String, Flow> entry : channelFlowMap.entrySet()) {
            Flow flow = entry.getValue();
            for (Integer key : flow.keys()) {
                try {
                    flow.get(key).cancel(true);
                } catch (Exception e) {
                    String requestKey = getRequestKey(entry.getKey(), key);
                    Request request = requests.get(requestKey);
                    if (request != null && request.getCompletableFuture() != null) {
                        request.getCompletableFuture().completeExceptionally(e);
                    }
                    flow.fail(e);
                } finally {
                    flow.remove(key);
                }
            }
            flow.close();
        }
        channels.values().forEach(channel -> {
            try {
                client.releaseChannel(channel, true);
            } catch (IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        });
        channelFlowMap.clear();
        channels.clear();
        requests.clear();
    }

    public SSLSession getSession() {
        return sslSession;
    }

    protected abstract String getRequestKey(String channelId, Integer streamId);

    Channel mapChannel(Request request) throws IOException {
        Channel channel;
        if (!client.hasPooledConnections()) {
            channel = channels.get(DUMMY);
            if (channel == null) {
                channel = switchNextChannel();
            }
            channels.put(DUMMY, channel);
        } else {
            channel = switchNextChannel();
            channels.put(request, channel);
        }
        SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
        sslSession = sslHandler != null ? sslHandler.engine().getSession() : null;
        return channel;
    }

    private Channel switchNextChannel() throws IOException {
        Channel channel = client.newChannel(httpAddress);
        if (channel != null) {
            channel.attr(TRANSPORT_ATTRIBUTE_KEY).set(this);
            waitForSettings();
        } else {
            ConnectException connectException;
            if (httpAddress != null) {
                connectException = new ConnectException("unable to connect to " + httpAddress);
            } else if (client.hasPooledConnections()) {
                connectException = new ConnectException("unable to get channel from pool");
            } else {
                // API misuse
                connectException = new ConnectException("unable to get channel");
            }
            this.throwable = connectException;
            throw connectException;
        }
        return channel;
    }

    protected Request continuation(Request request, HttpResponse httpResponse) throws URLSyntaxException {
        if (httpResponse == null) {
            return null;
        }
        if (request == null) {
            // push promise or something else
            return null;
        }
        try {
            if (request.canRedirect()) {
                int status = httpResponse.getStatus().getCode();
                switch (status) {
                    case 300:
                    case 301:
                    case 302:
                    case 303:
                    case 305:
                    case 307:
                    case 308:
                        String location = httpResponse.getHeaders().getHeader(HttpHeaderNames.LOCATION);
                        location = new PercentDecoder(StandardCharsets.UTF_8.newDecoder()).decode(location);
                        if (location != null) {
                            logger.log(Level.FINE, "found redirect location: " + location);
                            URL redirUrl = URL.base(request.url()).resolve(location);
                            HttpMethod method = httpResponse.getStatus().getCode() == 303 ? HttpMethod.GET : request.httpMethod();
                            Request.Builder newHttpRequestBuilder = Request.builder(method)
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
                            request.release();
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

    protected Request retry(Request request, HttpResponse httpResponse) {
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
            int status = httpResponse.getStatus ().getCode();
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

    @Override
    public void setCookieBox(CookieBox cookieBox) {
        this.cookieBox = cookieBox;
    }

    @Override
    public CookieBox getCookieBox() {
        return cookieBox;
    }

    void addCookie(Cookie cookie) {
        if (cookieBox == null) {
            this.cookieBox = new CookieBox(32);
        }
        cookieBox.put(cookie, true);
    }

    List<Cookie> matchCookiesFromBox(Request request) {
        return cookieBox == null ? Collections.emptyList() : cookieBox.keySet().stream().filter(cookie ->
                matchCookie(request.url(), cookie)
        ).collect(Collectors.toList());
    }

    List<Cookie> matchCookies(Request request) {
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

}
