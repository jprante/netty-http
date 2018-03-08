package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface Transport {

    AttributeKey<Transport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    Transport execute(Request request) throws IOException;

    <T> CompletableFuture<T> execute(Request request, Function<FullHttpResponse, T> supplier) throws IOException;

    Integer nextStream();

    void settingsReceived(Channel channel, Http2Settings http2Settings);

    void awaitSettings();

    void setCookieBox(Map<Cookie, Boolean> cookieBox);

    Map<Cookie, Boolean> getCookieBox();

    void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse);

    void headersReceived(Integer streamId, HttpHeaders httpHeaders);

    void pushPromiseReceived(Integer streamId, Integer promisedStreamId, Http2Headers headers);

    void awaitResponse(Integer streamId) throws IOException;

    Transport get();

    void success();

    void fail(Throwable throwable);

    boolean isFailed();

    Throwable getFailure();

    void close() throws IOException;
}
