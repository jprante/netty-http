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

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;

import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public final class HttpRequestContext implements HttpResponseListener, HttpRequestDefaults {

    private static final Logger logger = Logger.getLogger(HttpRequestContext.class.getName());

    private final URL url;

    private final HttpRequest httpRequest;

    private final AtomicBoolean succeeded;

    private final AtomicBoolean failed;

    private final boolean followRedirect;

    private final int maxRedirects;

    private final AtomicInteger redirectCount;

    private final Integer timeout;

    private final Long startTime;

    private final CountDownLatch latch;

    private final Integer streamId;

    private FullHttpResponse httpResponse;

    private Long stopTime;

    HttpRequestContext(URL url, HttpRequest httpRequest,
                       AtomicBoolean succeeded, AtomicBoolean failed,
                       int timeout, Long startTime,
                       boolean followRedirect, int maxRedirects, AtomicInteger redirectCount,
                       CountDownLatch latch, Integer streamId) {
        this.url = url;
        this.httpRequest = httpRequest;
        this.succeeded = succeeded;
        this.failed = failed;
        this.timeout = timeout;
        this.startTime = startTime;
        this.followRedirect = followRedirect;
        this.maxRedirects = maxRedirects;
        this.redirectCount = redirectCount;
        this.latch = latch;
        this.streamId = streamId;
    }

    HttpRequestContext(URL url, HttpRequest httpRequest, HttpRequestContext httpRequestContext) {
        this.url = url;
        this.httpRequest = httpRequest;
        this.succeeded = httpRequestContext.succeeded;
        this.failed = httpRequestContext.failed;
        this.failed.lazySet(false); // reset
        this.timeout = httpRequestContext.timeout;
        this.startTime = httpRequestContext.startTime;
        this.followRedirect = httpRequestContext.followRedirect;
        this.maxRedirects = httpRequestContext.maxRedirects;
        this.redirectCount = httpRequestContext.redirectCount;
        this.latch = httpRequestContext.latch;
        this.streamId = httpRequestContext.streamId;
    }

    public URL getURL() {
        return url;
    }

    public HttpRequest getHttpRequest() {
        return httpRequest;
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

    public Integer getStreamId() {
        return streamId;
    }

    public HttpRequestContext get() throws InterruptedException {
        return waitFor(DEFAULT_TIMEOUT_MILLIS, TimeUnit.SECONDS);
    }

    public HttpRequestContext waitFor(long value, TimeUnit timeUnit) throws InterruptedException {
        latch.await(value, timeUnit);
        stopTime = System.currentTimeMillis();
        return this;
    }

    public void success(String reason) {
        logger.log(Level.FINE, () -> "success because of " + reason);
        if (succeeded.compareAndSet(false, true)) {
            latch.countDown();

        }
    }

    public void fail(String reason) {
        logger.log(Level.FINE, () -> "failed because of " + reason);
        if (failed.compareAndSet(false, true)) {
            latch.countDown();
        }
    }

    @Override
    public void onResponse(FullHttpResponse fullHttpResponse) {
        this.httpResponse = fullHttpResponse;
    }

    public FullHttpResponse getHttpResponse() {
        return httpResponse;
    }

}
