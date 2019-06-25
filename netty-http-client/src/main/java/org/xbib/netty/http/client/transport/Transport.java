package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.client.Request;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface Transport {

    AttributeKey<Transport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    Transport execute(Request request) throws IOException;

    <T> CompletableFuture<T> execute(Request request, Function<FullHttpResponse, T> supplier) throws IOException;

    void waitForSettings();

    void settingsReceived(Http2Settings http2Settings) throws IOException;

    void responseReceived(Channel channel, Integer streamId, FullHttpResponse fullHttpResponse) throws IOException;

    void pushPromiseReceived(Channel channel, Integer streamId, Integer promisedStreamId, Http2Headers headers);

    void setCookieBox(Map<Cookie, Boolean> cookieBox);

    Map<Cookie, Boolean> getCookieBox();

    Transport get();

    Transport get(long value, TimeUnit timeUnit);

    void cancel();

    void fail(Throwable throwable);

    boolean isFailed();

    Throwable getFailure();

    SSLSession getSession();

    void close() throws IOException;
}
