package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;

import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpTransport extends BaseTransport implements Transport {

    private static final Logger logger = Logger.getLogger(HttpTransport.class.getName());

    private final AtomicInteger sequentialCounter;

    private SortedMap<Integer, CompletableFuture<Boolean>> sequentialPromiseMap;

    public HttpTransport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
        this.sequentialCounter = new AtomicInteger();
        this.sequentialPromiseMap = new ConcurrentSkipListMap<>();
    }

    @Override
    public Integer nextStream() {
        Integer streamId = sequentialCounter.getAndAdd(1);
        if (streamId == Integer.MIN_VALUE) {
            // reset if overflow, Java wraps atomic integers to Integer.MIN_VALUE
            sequentialCounter.set(0);
            streamId = 0;
        }
        sequentialPromiseMap.put(streamId, new CompletableFuture<>());
        return streamId;
    }

    @Override
    public void settingsReceived(Channel channel, Http2Settings http2Settings) {
    }

    @Override
    public void awaitSettings() {
    }

    @Override
    public void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse) {
        if (responseListener != null) {
            responseListener.onResponse(fullHttpResponse);
        }
        try {
            Request request = continuation(null, fullHttpResponse);
            if (request != null) {
                client.continuation(this, request);
            }
        } catch (URLSyntaxException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        if (!sequentialPromiseMap.isEmpty()) {
            CompletableFuture<Boolean> promise = sequentialPromiseMap.get(sequentialPromiseMap.firstKey());
            if (promise != null) {
                promise.complete(true);
            }
        }
    }

    @Override
    public void headersReceived(Integer streamId, HttpHeaders httpHeaders) {
        if (httpHeadersListener != null) {
            httpHeadersListener.onHeaders(httpHeaders);
        }
        for (String cookieString : httpHeaders.getAll(HttpHeaderNames.SET_COOKIE)) {
            Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
            addCookie(cookie);
            if (cookieListener != null) {
                cookieListener.onCookie(cookie);
            }
        }
    }

    @Override
    public void awaitResponse(Integer streamId) {
        if (streamId == null) {
            return;
        }
        CompletableFuture<Boolean> promise = sequentialPromiseMap.get(streamId);
        if (promise != null) {
            try {
                promise.get(client.getTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.log(Level.WARNING, "streamId=" + streamId + " " + e.getMessage(), e);
            } finally {
                sequentialPromiseMap.remove(streamId);
            }
        }
    }

    @Override
    public Transport get() {
        for (Integer streamId : sequentialPromiseMap.keySet()) {
            awaitResponse(streamId);
        }
        return this;
    }

    @Override
    public  void success() {
        for (CompletableFuture<Boolean> promise : sequentialPromiseMap.values()) {
            promise.complete(true);
        }
    }

    @Override
    public void fail(Throwable throwable) {
        if (exceptionListener != null) {
            exceptionListener.onException(throwable);
        }
        for (CompletableFuture<Boolean> promise : sequentialPromiseMap.values()) {
            promise.completeExceptionally(throwable);
        }
    }
}
