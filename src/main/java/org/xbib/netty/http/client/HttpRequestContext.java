/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.util.internal.PlatformDependent;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpPushListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;
import org.xbib.netty.http.client.util.LimitedHashSet;

import java.net.URI;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 *
 */
public final class HttpRequestContext implements HttpResponseListener, HttpRequestDefaults {

    private static final Logger logger = Logger.getLogger(HttpRequestContext.class.getName());

    private final URI uri;

    private final HttpRequest httpRequest;

    private final AtomicBoolean succeeded;

    private final AtomicBoolean failed;

    private final boolean followRedirect;

    private final int maxRedirects;

    private final AtomicInteger redirectCount;

    private final Integer timeout;

    private final Long startTime;

    private final CountDownLatch latch;

    private final AtomicInteger streamId;

    private final HttpResponseListener httpResponseListener;

    private final ExceptionListener exceptionListener;

    private final HttpHeadersListener httpHeadersListener;

    private final CookieListener cookieListener;

    private final HttpPushListener httpPushListener;

    private final Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> promiseMap;

    private final Map<Integer, Map.Entry<Http2Headers, ChannelPromise>> pushMap;

    private ChannelPromise settingsPromise;

    private Collection<Cookie> cookies;

    private Map<Integer, FullHttpResponse> httpResponses;

    private boolean hastimeout;

    private Long stopTime;

    HttpRequestContext(URI uri, HttpRequest httpRequest, AtomicInteger streamId,
                       AtomicBoolean succeeded, AtomicBoolean failed,
                       int timeout, Long startTime,
                       boolean followRedirect, int maxRedirects, AtomicInteger redirectCount,
                       CountDownLatch latch,
                       HttpResponseListener httpResponseListener,
                       ExceptionListener exceptionListener,
                       HttpHeadersListener httpHeadersListener,
                       CookieListener cookieListener,
                       HttpPushListener httpPushListener) {
        this.uri = uri;
        this.httpRequest = httpRequest;
        this.streamId = streamId;
        this.succeeded = succeeded;
        this.failed = failed;
        this.timeout = timeout;
        this.startTime = startTime;
        this.followRedirect = followRedirect;
        this.maxRedirects = maxRedirects;
        this.redirectCount = redirectCount;
        this.latch = latch;
        this.httpResponseListener = httpResponseListener;
        this.exceptionListener = exceptionListener;
        this.httpHeadersListener = httpHeadersListener;
        this.cookieListener = cookieListener;
        this.httpPushListener = httpPushListener;
        this.promiseMap = PlatformDependent.newConcurrentHashMap();
        this.pushMap = PlatformDependent.newConcurrentHashMap();
        this.cookies = new LimitedHashSet<>(10);
    }

    /**
     * A follow-up request to a given context with same stream ID (redirect).
     *
     */
    HttpRequestContext(URI uri, HttpRequest httpRequest, HttpRequestContext httpRequestContext) {
        this.uri = uri;
        this.httpRequest = httpRequest;
        this.streamId = httpRequestContext.streamId;
        this.succeeded = httpRequestContext.succeeded;
        this.failed = httpRequestContext.failed;
        this.failed.lazySet(false); // reset
        this.timeout = httpRequestContext.timeout;
        this.startTime = httpRequestContext.startTime;
        this.followRedirect = httpRequestContext.followRedirect;
        this.maxRedirects = httpRequestContext.maxRedirects;
        this.redirectCount = httpRequestContext.redirectCount;
        this.latch = httpRequestContext.latch;
        this.httpResponseListener = httpRequestContext.httpResponseListener;
        this.exceptionListener = httpRequestContext.exceptionListener;
        this.httpHeadersListener = httpRequestContext.httpHeadersListener;
        this.cookieListener = httpRequestContext.cookieListener;
        this.httpPushListener = httpRequestContext.httpPushListener;
        this.promiseMap = httpRequestContext.promiseMap;
        this.pushMap = httpRequestContext.pushMap;
        this.cookies = httpRequestContext.cookies;
    }

    public URI getURI() {
        return uri;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
    }

    public HttpResponseListener getHttpResponseListener() {
        return httpResponseListener;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public HttpHeadersListener getHttpHeadersListener() {
        return httpHeadersListener;
    }

    public CookieListener getCookieListener() {
        return cookieListener;
    }

    public HttpPushListener getHttpPushListener() {
        return httpPushListener;
    }

    public void setSettingsPromise(ChannelPromise settingsPromise) {
        this.settingsPromise = settingsPromise;
    }

    public ChannelPromise getSettingsPromise() {
        return settingsPromise;
    }

    public  Map<Integer, Map.Entry<ChannelFuture, ChannelPromise>> getStreamIdPromiseMap() {
        return promiseMap;
    }

    public void putStreamID(Integer streamId, ChannelFuture channelFuture, ChannelPromise channelPromise) {
        logger.log(Level.FINE, () -> "put stream ID " + streamId + " future = " + channelFuture);
        promiseMap.put(streamId, new AbstractMap.SimpleEntry<>(channelFuture, channelPromise));
    }

    public Map<Integer, Map.Entry<Http2Headers, ChannelPromise>> getPushMap() {
        return pushMap;
    }

    public void receiveStreamID(Integer streamId, Http2Headers headers, ChannelPromise channelPromise) {
        logger.log(Level.FINE, () -> "receive stream ID " + streamId + " " + headers);
        pushMap.put(streamId, new AbstractMap.SimpleEntry<>(headers, channelPromise));
    }

    public boolean isFinished() {
        return promiseMap.isEmpty() && pushMap.isEmpty();
    }

    public void addCookie(Cookie cookie) {
        cookies.add(cookie);
    }

    public Collection<Cookie> getCookies() {
        return cookies;
    }

    public List<Cookie> matchCookies() {
        return cookies.stream()
                .filter(this::matchCookie)
                .collect(Collectors.toList());
    }

    private boolean matchCookie(Cookie cookie) {
        boolean domainMatch = cookie.domain() == null || uri.getHost().endsWith(cookie.domain());
        if (!domainMatch) {
            return false;
        }
        boolean pathMatch = "/".equals(cookie.path()) || uri.getPath().startsWith(cookie.path());
        if (!pathMatch) {
            return false;
        }
        boolean secure = "https".equals(uri.getScheme());
        return (secure && cookie.isSecure()) || (!secure && !cookie.isSecure());
    }

    public int getTimeout() {
        return timeout;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isSucceeded() {
        return succeeded.get();
    }

    public boolean isFailed() {
        return failed.get();
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public AtomicInteger getRedirectCount() {
        return redirectCount;
    }

    public boolean isExpired() {
        return timeout != null && System.currentTimeMillis() > startTime + timeout;
    }

    public long took() {
        return stopTime != null ? stopTime - startTime : -1L;
    }

    public long remaining() {
        return (startTime + timeout) - System.currentTimeMillis();
    }

    public CountDownLatch getLatch() {
        return latch;
    }

    public AtomicInteger getStreamId() {
        return streamId;
    }

    public HttpRequestContext get() throws InterruptedException {
        return waitFor(DEFAULT_TIMEOUT_MILLIS, TimeUnit.SECONDS);
    }

    public HttpRequestContext waitFor(long value, TimeUnit timeUnit) throws InterruptedException {
        this.hastimeout = latch.await(value, timeUnit);
        stopTime = System.currentTimeMillis();
        return this;
    }

    public boolean isTimeout() {
        return hastimeout;
    }

    public void success(String reason) {
        logger.log(Level.FINE, () -> "success because of " + reason);
        if (succeeded.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    public void fail(String reason) {
        logger.log(Level.FINE, () -> "failed because of " + reason);
        IllegalStateException exception = new IllegalStateException(reason);
        if (exceptionListener != null) {
            exceptionListener.onException(exception);
        }
        if (failed.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    @Override
    public void onResponse(FullHttpResponse fullHttpResponse) {
        this.httpResponses.put(streamId.get(), fullHttpResponse);
    }

    public Map<Integer, FullHttpResponse> getHttpResponses() {
        return httpResponses;
    }
}
