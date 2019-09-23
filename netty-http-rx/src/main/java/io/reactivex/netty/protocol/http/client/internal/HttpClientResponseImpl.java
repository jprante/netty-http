/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.reactivex.netty.protocol.http.client.internal;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.util.ReferenceCountUtil;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.channel.ContentSource;
import io.reactivex.netty.protocol.http.CookiesHolder;
import io.reactivex.netty.protocol.http.HttpHandlerNames;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.internal.HttpContentSubscriberEvent;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import io.reactivex.netty.protocol.http.sse.client.ServerSentEventDecoder;
import rx.Observable;
import rx.Observable.Transformer;
import rx.Subscriber;
import rx.functions.Func1;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

public final class HttpClientResponseImpl<T> extends HttpClientResponse<T> {

    private static final Logger logger = Logger.getLogger(HttpClientResponseImpl.class.getName());

    public static final String KEEP_ALIVE_HEADER_NAME = "Keep-Alive";
    private static final Pattern PATTERN_COMMA = Pattern.compile(",");
    private static final Pattern PATTERN_EQUALS = Pattern.compile("=");
    public static final String KEEP_ALIVE_TIMEOUT_HEADER_ATTR = "timeout";

    private final HttpResponse nettyResponse;
    private final Connection<?, ?> connection;
    private final CookiesHolder cookiesHolder;
    private final ContentSource<T> contentSource;

    private HttpClientResponseImpl(HttpResponse nettyResponse) {
        this(nettyResponse, UnusableConnection.create());
    }

    private HttpClientResponseImpl(HttpResponse nettyResponse, Connection<?, ?> connection) {
        this.nettyResponse = nettyResponse;
        this.connection = connection;
        cookiesHolder = CookiesHolder.newClientResponseHolder(nettyResponse.headers());
        contentSource = new ContentSource<>(unsafeNettyChannel(), new ContentSourceSubscriptionFactory<T>());
    }

    private HttpClientResponseImpl(HttpClientResponseImpl<?> toCopy, ContentSource<T> newSource) {
        nettyResponse = toCopy.nettyResponse;
        connection = toCopy.connection;
        cookiesHolder = toCopy.cookiesHolder;
        contentSource = newSource;
    }

    @Override
    public HttpVersion getHttpVersion() {
        return nettyResponse.protocolVersion();
    }

    @Override
    public HttpResponseStatus getStatus() {
        return nettyResponse.status();
    }

    @Override
    public Map<String, Set<Cookie>> getCookies() {
        return cookiesHolder.getAllCookies();
    }

    @Override
    public boolean containsHeader(CharSequence name) {
        return nettyResponse.headers().contains(name);
    }

    @Override
    public boolean containsHeader(CharSequence name, CharSequence value, boolean ignoreCaseValue) {
        return nettyResponse.headers().contains(name, value, ignoreCaseValue);
    }

    @Override
    public Iterator<Entry<CharSequence, CharSequence>> headerIterator() {
        return nettyResponse.headers().iteratorCharSequence();
    }

    @Override
    public String getHeader(CharSequence name) {
        return nettyResponse.headers().get(name);
    }

    @Override
    public String getHeader(CharSequence name, String defaultValue) {
        return nettyResponse.headers().get(name, defaultValue);
    }

    @Override
    public List<String> getAllHeaderValues(CharSequence name) {
        return nettyResponse.headers().getAll(name);
    }

    @Override
    public long getContentLength() {
        return HttpUtil.getContentLength(nettyResponse);
    }

    @Override
    public long getContentLength(long defaultValue) {
        return HttpUtil.getContentLength(nettyResponse, defaultValue);
    }

    @Override
    public long getDateHeader(CharSequence name) {
        return nettyResponse.headers().getTimeMillis(name);
    }

    @Override
    public long getDateHeader(CharSequence name, long defaultValue) {
        return nettyResponse.headers().getTimeMillis(name, defaultValue);
    }

    @Override
    public String getHostHeader() {
        return nettyResponse.headers().get(HOST);
    }

    @Override
    public String getHost(String defaultValue) {
        return nettyResponse.headers().get(HOST, defaultValue);
    }

    @Override
    public int getIntHeader(CharSequence name) {
        return nettyResponse.headers().getInt(name);
    }

    @Override
    public int getIntHeader(CharSequence name, int defaultValue) {
        return nettyResponse.headers().getInt(name, defaultValue);
    }

    @Override
    public boolean isContentLengthSet() {
        return HttpUtil.isContentLengthSet(nettyResponse);
    }

    @Override
    public boolean isKeepAlive() {
        return HttpUtil.isKeepAlive(nettyResponse);
    }

    @Override
    public boolean isTransferEncodingChunked() {
        return HttpUtil.isTransferEncodingChunked(nettyResponse);
    }

    @Override
    public Set<String> getHeaderNames() {
        return nettyResponse.headers().names();
    }

    @Override
    public HttpClientResponse<T> addHeader(CharSequence name, Object value) {
        nettyResponse.headers().add(name, value);
        return this;
    }

    @Override
    public HttpClientResponse<T> addCookie(Cookie cookie) {
        nettyResponse.headers().add(SET_COOKIE, ClientCookieEncoder.STRICT.encode(cookie));
        return this;
    }

    @Override
    public HttpClientResponse<T> addDateHeader(CharSequence name, Date value) {
        nettyResponse.headers().set(name, value);
        return this;
    }

    @Override
    public HttpClientResponse<T> addDateHeader(CharSequence name, Iterable<Date> values) {
        for (Date value : values) {
            nettyResponse.headers().add(name, value);
        }
        return this;
    }

    @Override
    public HttpClientResponse<T> addHeader(CharSequence name, Iterable<Object> values) {
        nettyResponse.headers().add(name, values);
        return this;
    }

    @Override
    public HttpClientResponse<T> setDateHeader(CharSequence name, Date value) {
        nettyResponse.headers().set(name, value);
        return this;
    }

    @Override
    public HttpClientResponse<T> setHeader(CharSequence name, Object value) {
        nettyResponse.headers().set(name, value);
        return this;
    }

    @Override
    public HttpClientResponse<T> setDateHeader(CharSequence name, Iterable<Date> values) {
        for (Date value : values) {
            nettyResponse.headers().set(name, value);
        }
        return this;
    }

    @Override
    public HttpClientResponse<T> setHeader(CharSequence name, Iterable<Object> values) {
        nettyResponse.headers().set(name, values);
        return this;
    }

    @Override
    public HttpClientResponse<T> removeHeader(CharSequence name) {
        nettyResponse.headers().remove(name);
        return this;
    }

    @Override
    public ContentSource<ServerSentEvent> getContentAsServerSentEvents() {
        if (containsHeader(CONTENT_TYPE) && getHeader(CONTENT_TYPE).startsWith("text/event-stream")) {
            ChannelPipeline pipeline = unsafeNettyChannel().pipeline();
            ChannelHandlerContext decoderCtx = pipeline.context(HttpHandlerNames.HttpClientCodec.getName());
            if (null != decoderCtx) {
                pipeline.addAfter(decoderCtx.name(), HttpHandlerNames.SseClientCodec.getName(),
                                  new ServerSentEventDecoder());
            }
            return new ContentSource<>(unsafeNettyChannel(), new ContentSourceSubscriptionFactory<ServerSentEvent>());
        }

        return new ContentSource<>(new IllegalStateException("Response is not a server sent event response."));
    }

    @Override
    public ContentSource<T> getContent() {
        return contentSource;
    }

    @Override
    public Observable<Void> discardContent() {
        return getContent().map(new Func1<T, Void>() {
            @Override
            public Void call(T t) {
                ReferenceCountUtil.release(t);
                return null;
            }
        }).ignoreElements();
    }

    @Override
    public <TT> HttpClientResponse<TT> transformContent(Transformer<T, TT> transformer) {
        return new HttpClientResponseImpl<>(this, contentSource.transform(transformer));
    }

    @Override
    public Channel unsafeNettyChannel() {
        return unsafeConnection().unsafeNettyChannel();
    }

    @Override
    public Connection<?, ?> unsafeConnection() {
        return connection;
    }

    /**
     * Parses the timeout value from the HTTP keep alive header (with name {@link #KEEP_ALIVE_HEADER_NAME}) as described in
     * <a href="http://tools.ietf.org/id/draft-thomson-hybi-http-timeout-01.html">this spec</a>
     *
     * @return The keep alive timeout or {@code null} if this response does not define the appropriate header value.
     */
    public Long getKeepAliveTimeoutSeconds() {
        String keepAliveHeader = nettyResponse.headers().get(KEEP_ALIVE_HEADER_NAME);
        if (null != keepAliveHeader && !keepAliveHeader.isEmpty()) {
            String[] pairs = PATTERN_COMMA.split(keepAliveHeader);
            if (pairs != null) {
                for (String pair: pairs) {
                    String[] nameValue = PATTERN_EQUALS.split(pair.trim());
                    if (nameValue != null && nameValue.length >= 2) {
                        String name = nameValue[0].trim().toLowerCase();
                        String value = nameValue[1].trim();
                        if (KEEP_ALIVE_TIMEOUT_HEADER_ATTR.equals(name)) {
                            try {
                                return Long.valueOf(value);
                            } catch (NumberFormatException e) {
                                logger.log(Level.INFO, "Invalid HTTP keep alive timeout value. Keep alive header: "
                                            + keepAliveHeader + ", timeout attribute value: " + nameValue[1], e);
                                return null;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /*Visible for the client bridge*/static <C> HttpClientResponseImpl<C> unsafeCreate(HttpResponse nettyResponse) {
        return new HttpClientResponseImpl<>(nettyResponse);
    }

    public static <C> HttpClientResponse<C> newInstance(HttpClientResponse<C> unsafeInstance,
                                                        Connection<?, ?> connection) {
        HttpClientResponseImpl<C> cast = (HttpClientResponseImpl<C>) unsafeInstance;
        return new HttpClientResponseImpl<>(cast.nettyResponse, connection);
    }

    public static <C> HttpClientResponse<C> newInstance(HttpResponse nettyResponse, Connection<?, ?> connection) {
        return new HttpClientResponseImpl<>(nettyResponse, connection);
    }

    private static class ContentSourceSubscriptionFactory<T> implements Func1<Subscriber<? super T>, Object> {
        @Override
        public Object call(Subscriber<? super T> subscriber) {
            return new HttpContentSubscriberEvent<>(subscriber);
        }
    }
}
