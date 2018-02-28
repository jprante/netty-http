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

public class Http2Transport extends BaseTransport implements Transport {

    private static final Logger logger = Logger.getLogger(Http2Transport.class.getName());

    private CompletableFuture<Boolean> settingsPromise;

    private final AtomicInteger streamIdCounter;

    private SortedMap<Integer, CompletableFuture<Boolean>> streamidPromiseMap;

    public Http2Transport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
        streamIdCounter = new AtomicInteger(3);
        streamidPromiseMap = new ConcurrentSkipListMap<>();
    }

    @Override
    public void connect() throws InterruptedException {
        super.connect();
        settingsPromise = new CompletableFuture<>();
    }

    @Override
    public Integer nextStream() {
        Integer streamId = streamIdCounter.getAndAdd(2);
        if (streamId == Integer.MIN_VALUE) {
            // reset if overflow, Java wraps atomic integers to Integer.MIN_VALUE
            streamIdCounter.set(3);
            streamId = 3;
        }
        streamidPromiseMap.put(streamId, new CompletableFuture<>());
        return streamId;
    }

    @Override
    public void settingsReceived(Channel channel, Http2Settings http2Settings) {
        if (settingsPromise != null) {
            settingsPromise.complete(true);
        } else {
            logger.log(Level.WARNING, "settings received but no promise present");
        }
    }

    @Override
    public void awaitSettings() {
        if (settingsPromise != null) {
            try {
                settingsPromise.get(client.getTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                settingsPromise.completeExceptionally(e);
            }
        } else {
            logger.log(Level.WARNING, "waiting for settings but no promise present");
        }
    }

    @Override
    public void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse) {
        if (streamId == null) {
            logger.log(Level.WARNING, "unexpected message received: " + fullHttpResponse);
            return;
        }
        CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
        if (promise == null) {
            logger.log(Level.WARNING, "message received for unknown stream id " + streamId);
            if (pushListener != null) {
                pushListener.onPushReceived(null, fullHttpResponse);
            }
        } else {
            if (responseListener != null) {
                responseListener.onResponse(fullHttpResponse);
            }
            // forward?
            try {
                Request request = continuation(streamId, fullHttpResponse);
                if (request != null) {
                    // synchronous call here
                    client.continuation(this, request);
                }
            } catch (URLSyntaxException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            // complete origin transport
            promise.complete(true);
        }
    }

    @Override
    public void headersReceived(Integer streamId, HttpHeaders httpHeaders) {
        if (httpHeadersListener != null) {
            httpHeadersListener.onHeaders(httpHeaders);
        }
        if (cookieListener != null) {
            for (String cookieString : httpHeaders.getAll(HttpHeaderNames.SET_COOKIE)) {
                Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
                cookieListener.onCookie(cookie);
            }
        }
    }

    @Override
    public void awaitResponse(Integer streamId) {
        if (streamId == null) {
            return;
        }
        CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
        if (promise != null) {
            try {
                promise.get(client.getTimeout(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.log(Level.WARNING, "streamId=" + streamId + " " + e.getMessage(), e);
            } finally {
                streamidPromiseMap.remove(streamId);
            }
        }
    }

    @Override
    public Transport get() {
        for (Integer streamId : streamidPromiseMap.keySet()) {
            awaitResponse(streamId);
        }
        return this;
    }

    @Override
    public  void success() {
        for (CompletableFuture<Boolean> promise : streamidPromiseMap.values()) {
            promise.complete(true);
        }
    }

    @Override
    public void fail(Throwable throwable) {
        if (exceptionListener != null) {
            exceptionListener.onException(throwable);
        }
        for (CompletableFuture<Boolean> promise : streamidPromiseMap.values()) {
            promise.completeExceptionally(throwable);
        }
    }
}
