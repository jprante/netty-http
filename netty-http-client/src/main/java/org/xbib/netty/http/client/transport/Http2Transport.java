package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.handler.http2.Http2ResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2StreamFrameToHttpObjectCodec;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.ResponseListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2Transport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(Http2Transport.class.getName());

    private CompletableFuture<Boolean> settingsPromise;

    private final ChannelInitializer<Channel> initializer;

    public Http2Transport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
        this.settingsPromise = httpAddress != null ? new CompletableFuture<>() : null;
        final Transport transport = this;
        this.initializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch)  {
                ch.attr(TRANSPORT_ATTRIBUTE_KEY).set(transport);
                ChannelPipeline p = ch.pipeline();
                p.addLast("child-client-frame-converter",
                        new Http2StreamFrameToHttpObjectCodec(false));
                p.addLast("child-client-chunk-aggregator",
                        new HttpObjectAggregator(client.getClientConfig().getMaxContentLength()));
                p.addLast("child-client-response-handler",
                        new Http2ResponseHandler());
            }
        };
    }

    @Override
    public Transport execute(Request request) throws IOException {
        Channel channel = mapChannel(request);
        if (throwable != null) {
            return this;
        }
        final String channelId = channel.id().toString();
        channelFlowMap.putIfAbsent(channelId, new Flow());
        Http2StreamChannel childChannel = new Http2StreamChannelBootstrap(channel)
                .handler(initializer).open().syncUninterruptibly().getNow();
        String authority = request.url().getHost() + (request.url().getPort() != null ? ":" + request.url().getPort() : "");
        String path = request.url().getPath() != null && !request.url().getPath().isEmpty() ?
                request.url().getPath() : "/";
        Http2Headers http2Headers = new DefaultHttp2Headers()
                .method(request.httpMethod().asciiName())
                .scheme(request.url().getScheme())
                .authority(authority)
                .path(path);
        final Integer streamId = channelFlowMap.get(channelId).nextStreamId();
        if (streamId == null) {
            throw new IllegalStateException();
        }
        requests.put(getRequestKey(channelId, streamId), request);
        http2Headers.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
        // add matching cookies from box (previous requests) and new cookies from request builder
        Collection<Cookie> cookies = new ArrayList<>();
        cookies.addAll(matchCookiesFromBox(request));
        cookies.addAll(matchCookies(request));
        if (!cookies.isEmpty()) {
            request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(cookies));
        }
        // add stream-id and cookie headers
        HttpConversionUtil.toHttp2Headers(request.headers(), http2Headers);
        boolean hasContent = request.content() != null && request.content().readableBytes() > 0;
        DefaultHttp2HeadersFrame headersFrame = new DefaultHttp2HeadersFrame(http2Headers, !hasContent);
        childChannel.write(headersFrame);
        if (hasContent) {
            DefaultHttp2DataFrame dataFrame = new DefaultHttp2DataFrame(request.content(), true);
            childChannel.write(dataFrame);
        }
        childChannel.flush();
        if (client.hasPooledConnections()) {
            client.releaseChannel(channel, false);
        }
        return this;
    }

    @Override
    public void settingsReceived(Http2Settings http2Settings) {
        if (settingsPromise != null) {
            settingsPromise.complete(true);
        } else {
            logger.log(Level.WARNING, "settings received but no promise present");
        }
    }

    @Override
    public void waitForSettings() {
        if (settingsPromise != null) {
            try {
                settingsPromise.get(client.getClientConfig().getReadTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.log(Level.WARNING, "timeout in client while waiting for settings");
                settingsPromise.completeExceptionally(e);
            } catch (InterruptedException | ExecutionException e) {
                settingsPromise.completeExceptionally(e);
            }
        }
    }

    @Override
    public void responseReceived(Channel channel, Integer streamId, FullHttpResponse fullHttpResponse) {
        if (throwable != null) {
            logger.log(Level.WARNING, "throwable not null for response " + fullHttpResponse, throwable);
            return;
        }
        if (streamId == null) {
            logger.log(Level.WARNING, "stream ID is null for response " + fullHttpResponse);
            return;
        }
        // format of childchan channel ID is <parent channel ID> "/" <substream ID>
        String channelId = channel.id().toString();
        int pos = channelId.indexOf('/');
        channelId = pos > 0 ? channelId.substring(0, pos) : channelId;
        Flow flow = channelFlowMap.get(channelId);
        if (flow == null) {
            return;
        }
        String requestKey = getRequestKey(channelId, streamId);
        CompletableFuture<Boolean> promise = flow.get(streamId);
        if (promise != null) {
            Request request = requests.get(requestKey);
            if (request == null) {
                promise.completeExceptionally(new IllegalStateException());
            } else {
                for (String cookieString : fullHttpResponse.headers().getAll(HttpHeaderNames.SET_COOKIE)) {
                    Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
                    addCookie(cookie);
                    CookieListener cookieListener = request.getCookieListener();
                    if (cookieListener != null) {
                        cookieListener.onCookie(cookie);
                    }
                }
                ResponseListener responseListener = request.getResponseListener();
                if (responseListener != null) {
                    responseListener.onResponse(fullHttpResponse);
                }
                try {
                    Request retryRequest = retry(request, fullHttpResponse);
                    if (retryRequest != null) {
                        // retry transport, wait for completion
                        client.retry(this, retryRequest);
                    } else {
                        Request continueRequest = continuation(request, fullHttpResponse);
                        if (continueRequest != null) {
                            // continue with new transport, synchronous call here, wait for completion
                            client.continuation(this, continueRequest);
                        }
                    }
                    promise.complete(true);
                } catch (URLSyntaxException | IOException e) {
                    promise.completeExceptionally(e);
                }
            }
        }
        channelFlowMap.get(channelId).remove(streamId);
        requests.remove(requestKey);
    }

    @Override
    public void pushPromiseReceived(Channel channel, Integer streamId, Integer promisedStreamId, Http2Headers headers) {
        String channelId = channel.id().toString();
        channelFlowMap.get(channelId).put(promisedStreamId, new CompletableFuture<>());
        String requestKey = getRequestKey(channel.id().toString(), promisedStreamId);
        requests.put(requestKey, requests.get(requestKey));
    }

    @Override
    protected String getRequestKey(String channelId, Integer streamId) {
        return channelId + "#" + streamId;
    }
}
