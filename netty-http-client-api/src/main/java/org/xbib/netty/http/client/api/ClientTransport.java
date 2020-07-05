package org.xbib.netty.http.client.api;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.util.AttributeKey;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.common.Transport;
import org.xbib.netty.http.common.cookie.CookieBox;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public interface ClientTransport extends Transport {

    AttributeKey<ClientTransport> TRANSPORT_ATTRIBUTE_KEY = AttributeKey.valueOf("transport");

    HttpAddress getHttpAddress();

    ClientTransport execute(Request request) throws IOException;

    <T> CompletableFuture<T> execute(Request request, Function<HttpResponse, T> supplier) throws IOException;

    void waitForSettings();

    void settingsReceived(Http2Settings http2Settings) throws IOException;

    void responseReceived(Channel channel, Integer streamId, FullHttpResponse fullHttpResponse) throws IOException;

    void pushPromiseReceived(Channel channel, Integer streamId, Integer promisedStreamId, Http2Headers headers);

    void fail(Channel channel, Throwable throwable);

    void inactive(Channel channel);

    void setCookieBox(CookieBox cookieBox);

    CookieBox getCookieBox();

    ClientTransport get();

    ClientTransport get(long value, TimeUnit timeUnit);

    void cancel();

    boolean isFailed();

    Throwable getFailure();

    SSLSession getSession();

    void close() throws IOException;

}
