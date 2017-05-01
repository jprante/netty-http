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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

import java.io.Closeable;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Netty HTTP client.
 */
public final class HttpClient implements Closeable {

    private static final Logger logger = Logger.getLogger(HttpClient.class.getName());

    private final ByteBufAllocator byteBufAllocator;

    private final EventLoopGroup eventLoopGroup;

    private final HttpClientChannelPoolMap poolMap;

    /**
     * Create a new HTTP client.
     */
    HttpClient(ByteBufAllocator byteBufAllocator,
                       EventLoopGroup eventLoopGroup,
                       Bootstrap bootstrap,
                       int maxConnections,
                       HttpClientChannelContext httpClientChannelContext) {
        this.byteBufAllocator = byteBufAllocator;
        this.eventLoopGroup = eventLoopGroup;
        this.poolMap = new HttpClientChannelPoolMap(this, httpClientChannelContext, bootstrap, maxConnections);
    }

    /**
     * Create a builder to configure connecting.
     *
     * @return A builder
     */
    public static HttpClientBuilder builder() {
        return new HttpClientBuilder();
    }

    public HttpClientRequestBuilder prepareRequest(HttpMethod method) {
        return new HttpClientRequestBuilder(this, method, byteBufAllocator);
    }

    /**
     * Prepare a HTTP GET request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder prepareGet() {
        return prepareRequest(HttpMethod.GET);
    }

    /**
     * Prepare a HTTP HEAD request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder prepareHead() {
        return prepareRequest(HttpMethod.HEAD);
    }

    /**
     * Prepare a HTTP PUT request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder preparePut() {
        return prepareRequest(HttpMethod.PUT);
    }

    /**
     * Prepare a HTTP POST request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder preparePost() {
        return prepareRequest(HttpMethod.POST);
    }

    /**
     * Prepare a HTTP DELETE request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder prepareDelete() {
        return prepareRequest(HttpMethod.DELETE);
    }

    /**
     * Prepare a HTTP OPTIONS request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder prepareOptions() {
        return prepareRequest(HttpMethod.OPTIONS);
    }

    /**
     * Prepare a HTTP PATCH request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder preparePatch() {
        return prepareRequest(HttpMethod.PATCH);
    }

    /**
     * Prepare a HTTP TRACE request.
     *
     * @return a request builder
     */
    public HttpClientRequestBuilder prepareTrace() {
        return prepareRequest(HttpMethod.TRACE);
    }

    public HttpClientChannelPoolMap poolMap() {
        return poolMap;
    }

    /**
     * Close client.
     */
    public void close() {
        logger.log(Level.FINE, () -> "closing pool map");
        poolMap.close();
        logger.log(Level.FINE, () -> "closing event loop group");
        if (!eventLoopGroup.isTerminated()) {
            eventLoopGroup.shutdownGracefully();
        }
        logger.log(Level.FINE, () -> "closed");
    }

    void dispatch(HttpRequestContext httpRequestContext, HttpResponseListener httpResponseListener,
                  ExceptionListener exceptionListener) {
        final URL url = httpRequestContext.getURL();
        final HttpRequest httpRequest = httpRequestContext.getHttpRequest();
        logger.log(Level.FINE, () -> "trying URL " + url);
        if (httpRequestContext.isExpired()) {
            httpRequestContext.fail("request expired");
        }
        if (httpRequestContext.isFailed()) {
            logger.log(Level.FINE, () -> "request is cancelled");
            return;
        }
        HttpVersion version = httpRequestContext.getHttpRequest().protocolVersion();
        InetAddressKey inetAddressKey = new InetAddressKey(url, version);
        // effectivly disable pool for HTTP/2
        if (version.majorVersion() == 2) {
            poolMap.remove(inetAddressKey);
        }
        final FixedChannelPool pool = poolMap.get(inetAddressKey);
        logger.log(Level.FINE, () -> "connecting to " + inetAddressKey);
        Future<Channel> futureChannel = pool.acquire();
        futureChannel.addListener((FutureListener<Channel>) future -> {
            if (future.isSuccess()) {
                Channel channel = future.getNow();
                channel.attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).set(pool);
                channel.attr(HttpClientChannelContext.REQUEST_CONTEXT_ATTRIBUTE_KEY).set(httpRequestContext);
                if (httpResponseListener != null) {
                    channel.attr(HttpClientChannelContext.RESPONSE_LISTENER_ATTRIBUTE_KEY).set(httpResponseListener);
                }
                if (exceptionListener != null) {
                    channel.attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).set(exceptionListener);
                }
                if (httpRequestContext.isFailed()) {
                    logger.log(Level.FINE, () -> "detected fail, close now");
                    future.cancel(true);
                    if (channel.isOpen()) {
                        channel.close();
                    }
                    logger.log(Level.FINE, () -> "release channel to pool");
                    pool.release(channel);
                    return;
                }
                if (httpRequest.protocolVersion().majorVersion() == 1) {
                    logger.log(Level.FINE, "HTTP1: write and flush " + httpRequest.toString());
                    channel.writeAndFlush(httpRequest)
                            .addListener((ChannelFutureListener) future1 -> {
                                if (httpRequestContext.isFailed()) {
                                    logger.log(Level.FINE, () -> "detected fail, close now");
                                    future1.cancel(true);
                                    if (future1.channel().isOpen()) {
                                        future1.channel().close();
                                    }
                                }
                            });
                } else if (httpRequest.protocolVersion().majorVersion() == 2) {
                    HttpClientChannelInitializer.Http2SettingsHandler http2SettingsHandler =
                            poolMap.getHttpClientChannelInitializer().getHttp2SettingsHandler();
                    if (http2SettingsHandler != null) {
                        logger.log(Level.FINE, "HTTP2: waiting for settings");
                        http2SettingsHandler.awaitSettings(httpRequestContext, exceptionListener);
                    }
                    Http2Handler http2Handler = poolMap.getHttpClientChannelInitializer().getHttp2Handler();
                    if (http2Handler != null) {
                        logger.log(Level.FINE, () ->
                                "HTTP2: trying to write, streamID=" + httpRequestContext.getStreamId() +
                                        " request: " + httpRequest.toString());
                        ChannelPromise channelPromise = channel.newPromise();
                        http2Handler.put(httpRequestContext.getStreamId(), channel.write(httpRequest), channelPromise);
                        channel.flush();
                        logger.log(Level.FINE, "HTTP2: waiting for responses");
                        http2Handler.awaitResponses(httpRequestContext, exceptionListener);
                    }
                }
            } else {
                if (exceptionListener != null) {
                    exceptionListener.onException(future.cause());
                }
                httpRequestContext.fail("channel pool failure");
            }
        });
    }

    boolean tryRedirect(Channel channel, FullHttpResponse httpResponse, HttpRequestContext httpRequestContext)
            throws IOException {
        if (httpRequestContext.isFollowRedirect()) {
            String redirUrl = findRedirect(httpRequestContext, httpResponse);
            if (redirUrl != null) {
                HttpMethod method = httpResponse.status().code() == 303 ? HttpMethod.GET :
                        httpRequestContext.getHttpRequest().method();
                if (httpRequestContext.getRedirectCount().getAndIncrement() < httpRequestContext.getMaxRedirects()) {
                    dispatchRedirect(channel, method, new URL(redirUrl), httpRequestContext);
                } else {
                    httpRequestContext.fail("too many redirections");
                    final ChannelPool channelPool =
                            channel.attr(HttpClientChannelContext.CHANNEL_POOL_ATTRIBUTE_KEY).get();
                    channelPool.release(channel);
                }
                return true;
            }
        }
        return false;
    }

    private String findRedirect(HttpRequestContext httpRequestContext, HttpResponse httpResponse)
            throws IOException {
        if (httpResponse == null) {
            return null;
        }
        switch (httpResponse.status().code()) {
            case 300:
            case 301:
            case 302:
            case 303:
            case 305:
            case 307:
            case 308:
                String location = URLDecoder.decode(httpResponse.headers().get(HttpHeaderNames.LOCATION), "UTF-8");
                if (location != null && (location.toLowerCase().startsWith("http://") ||
                        location.toLowerCase().startsWith("https://"))) {
                    logger.log(Level.FINE, "(absolute) redirect to " + location);
                    return location;
                } else {
                    logger.log(Level.FINE, "(relative->absolute) redirect to " + location);
                    return makeAbsolute(httpRequestContext.getURL(), location);
                }
            default:
                break;
        }
        return null;
    }

    private void dispatchRedirect(Channel channel, HttpMethod method, URL url,
                                  HttpRequestContext httpRequestContext) {
        final String uri = httpRequestContext.getHttpRequest().protocolVersion().majorVersion() == 2 ?
                url.toExternalForm() : makeRelative(url);
        final HttpRequest httpRequest;
        if (method.equals(httpRequestContext.getHttpRequest().method()) &&
                httpRequestContext.getHttpRequest() instanceof DefaultFullHttpRequest) {
            DefaultFullHttpRequest defaultFullHttpRequest = (DefaultFullHttpRequest) httpRequestContext.getHttpRequest();
            FullHttpRequest fullHttpRequest = defaultFullHttpRequest.copy();
            fullHttpRequest.setUri(uri);
            httpRequest = fullHttpRequest;
        } else {
            httpRequest = new DefaultHttpRequest(httpRequestContext.getHttpRequest().protocolVersion(), method, uri);
        }
        for (Map.Entry<String, String> e : httpRequestContext.getHttpRequest().headers().entries()) {
            httpRequest.headers().add(e.getKey(), e.getValue());
        }
        httpRequest.headers().set(HttpHeaderNames.HOST, url.getHost());
        HttpRequestContext redirectContext = new HttpRequestContext(url, httpRequest,
                httpRequestContext);
        logger.log(Level.FINE, "dispatchRedirect url = " + url + " with new request " + httpRequest.toString());
        HttpResponseListener httpResponseListener =
                channel.attr(HttpClientChannelContext.RESPONSE_LISTENER_ATTRIBUTE_KEY).get();
        ExceptionListener exceptionListener =
                channel.attr(HttpClientChannelContext.EXCEPTION_LISTENER_ATTRIBUTE_KEY).get();
        dispatch(redirectContext, httpResponseListener, exceptionListener);
    }

    private String makeRelative(URL base) {
        String uri = base.getPath();
        if (base.getQuery() != null) {
            uri = uri + "?" + base.getQuery();
        }
        return uri;
    }

    private String makeAbsolute(URL base, String location) throws UnsupportedEncodingException {
        String path = base.getPath() == null ? "/" : URLDecoder.decode(base.getPath(), "UTF-8");
        if (location.startsWith("/")) {
            path = location;
        } else if (path.endsWith("/")) {
            path += location;
        } else {
            path += "/" + location;
        }
        String scheme = base.getProtocol();
        StringBuilder sb = new StringBuilder(scheme).append("://").append(base.getHost());
        int defaultPort = "http".equals(scheme) ? 80 : "https".equals(scheme) ? 443 : -1;
        if (defaultPort != -1 && base.getPort() != -1 && defaultPort != base.getPort()) {
            sb.append(":").append(base.getPort());
        }
        if (path.charAt(0) != '/') {
            sb.append('/');
        }
        sb.append(path);
        return sb.toString();
    }
}
