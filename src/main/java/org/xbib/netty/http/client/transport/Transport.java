package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ExceptionListener;
import org.xbib.netty.http.client.listener.HttpHeadersListener;
import org.xbib.netty.http.client.listener.HttpPushListener;
import org.xbib.netty.http.client.listener.HttpResponseListener;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface Transport {

    AttributeKey<Transport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    HttpAddress httpAddress();

    void connect() throws InterruptedException;

    Transport execute(Request request);

    <T> CompletableFuture<T> execute(Request request, Function<FullHttpResponse, T> supplier);

    Channel channel();

    Integer nextStream();

    void settingsReceived(Channel channel, Http2Settings http2Settings);

    void awaitSettings();

    void setResponseListener(HttpResponseListener responseListener);

    HttpResponseListener getResponseListener();

    void setExceptionListener(ExceptionListener exceptionListener);

    ExceptionListener getExceptionListener();

    void setHeadersListener(HttpHeadersListener headersListener);

    HttpHeadersListener getHeadersListener();

    void setPushListener(HttpPushListener pushListener);

    HttpPushListener getPushListener();

    void setCookieListener(CookieListener cookieListener);

    CookieListener getCookieListener();

    void setCookieBox(Map<Cookie, Boolean> cookieBox);

    Map<Cookie, Boolean> getCookieBox();

    void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse);

    void headersReceived(Integer streamId, HttpHeaders httpHeaders);

    void awaitResponse(Integer streamId);

    Transport get();

    void success();

    void fail(Throwable throwable);

    void close();
}
