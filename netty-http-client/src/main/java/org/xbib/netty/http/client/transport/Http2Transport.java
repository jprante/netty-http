package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Transport;
import org.xbib.netty.http.client.cookie.ClientCookieDecoder;
import org.xbib.netty.http.client.cookie.ClientCookieEncoder;
import org.xbib.netty.http.client.handler.http2.Http2ResponseHandler;
import org.xbib.netty.http.client.handler.http2.Http2StreamFrameToHttpObjectCodec;
import org.xbib.netty.http.common.DefaultHttpResponse;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.cookie.Cookie;

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
        this.initializer = new ChannelInitializer<>() {
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
        flowMap.putIfAbsent(channelId, new Flow());
        Http2StreamChannel childChannel = new Http2StreamChannelBootstrap(channel)
                .handler(initializer).open().syncUninterruptibly().getNow();
        AsciiString method = request.httpMethod().asciiName();
        String scheme = request.url().getScheme();
        String authority = request.url().getHost() + (request.url().getPort() != null ? ":" + request.url().getPort() : "");
        String path = request.relative().isEmpty() ? "/" : request.relative();
        Http2Headers http2Headers = new DefaultHttp2Headers()
                .method(method).scheme(scheme).authority(authority).path(path);
        final Integer streamId = flowMap.get(channelId).nextStreamId();
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
        client.getRequestCounter().incrementAndGet();
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
            logger.log(Level.WARNING, "throwable is not null?", throwable);
            return;
        }
        if (streamId == null) {
            logger.log(Level.WARNING, "stream ID is null?");
            return;
        }
        DefaultHttpResponse httpResponse = null;
        client.getResponseCounter().incrementAndGet();
        try {
            // format of childchan channel ID is <parent channel ID> "/" <substream ID>
            String channelId = channel.id().toString();
            int pos = channelId.indexOf('/');
            channelId = pos > 0 ? channelId.substring(0, pos) : channelId;
            Flow flow = flowMap.get(channelId);
            if (flow == null) {
                // should never happen since we keep the channelFlowMap around
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "flow is null? channelId = " + channelId);
                }
                return;
            }
            Request request = requests.remove(getRequestKey(channelId, streamId));
            if (request == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "request is null? channelId = " + channelId + " streamId = " + streamId);
                }
                // even if request is null, we may complete the flow with an exception
                CompletableFuture<Boolean> promise = flow.get(streamId);
                if (promise != null) {
                    promise.completeExceptionally(new IllegalStateException("no request"));
                }
            } else {
                for (String cookieString : fullHttpResponse.headers().getAll(HttpHeaderNames.SET_COOKIE)) {
                    Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
                    addCookie(cookie);
                }
                httpResponse = new DefaultHttpResponse(httpAddress, fullHttpResponse, getCookieBox());
                CompletableFuture<Boolean> promise = flow.get(streamId);
                try {
                    request.onResponse(httpResponse);
                    Request retryRequest = retry(request, httpResponse);
                    if (retryRequest != null) {
                        // retry transport, wait for completion
                        client.retry(this, retryRequest);
                    } else {
                        Request continueRequest = continuation(request, httpResponse);
                        if (continueRequest != null) {
                            // continue with new transport, synchronous call here, wait for completion
                            client.continuation(this, continueRequest);
                        }
                    }
                    if (promise != null) {
                        promise.complete(true);
                    } else {
                        // when transport is closed, flow map will be emptied
                        logger.log(Level.FINE, "promise is null, flow lost");
                    }
                } catch (URLSyntaxException | IOException e) {
                    if (promise != null) {
                        promise.completeExceptionally(e);
                    } else {
                        logger.log(Level.FINE, "promise is null, can't abort flow");
                    }
                } finally {
                    flow.remove(streamId);
                }
            }
        } finally {
            if (httpResponse != null) {
                httpResponse.release();
            }
        }
    }

    @Override
    public void pushPromiseReceived(Channel channel, Integer streamId, Integer promisedStreamId, Http2Headers headers) {
        String channelId = channel.id().toString();
        flowMap.get(channelId).put(promisedStreamId, new CompletableFuture<>());
        String requestKey = getRequestKey(channel.id().toString(), streamId);
        requests.put(requestKey, requests.get(requestKey));
    }

    @Override
    protected String getRequestKey(String channelId, Integer streamId) {
        return channelId + "#" + streamId;
    }
}
