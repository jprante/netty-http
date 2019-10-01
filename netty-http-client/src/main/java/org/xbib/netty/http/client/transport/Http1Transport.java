package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Transport;
import org.xbib.netty.http.client.cookie.ClientCookieDecoder;
import org.xbib.netty.http.client.cookie.ClientCookieEncoder;
import org.xbib.netty.http.common.DefaultHttpResponse;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.cookie.Cookie;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http1Transport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(Http1Transport.class.getName());

    public Http1Transport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
    }

    @Override
    public Transport execute(Request request) throws IOException {
        Channel channel = mapChannel(request);
        if (throwable != null) {
            return this;
        }
        final String channelId = channel.id().toString();
        flowMap.putIfAbsent(channelId, new Flow());
        // Some HTTP 1 servers do not understand URIs in HTTP command line in spite of RFC 7230.
        // The "origin form" requires a "Host" header.
        // Our algorithm is: use always "origin form" for HTTP 1, use absolute form for HTTP 2.
        // The reason is that Netty derives the HTTP/2 scheme header from the absolute form.
        String uri = request.httpVersion().majorVersion() == 1 ? request.relative() : request.absolute();
        FullHttpRequest fullHttpRequest = request.content() == null ?
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri) :
                new DefaultFullHttpRequest(request.httpVersion(), request.httpMethod(), uri, request.content());
        HttpPostRequestEncoder httpPostRequestEncoder = null;
        final Integer streamId = flowMap.get(channelId).nextStreamId();
        if (streamId == null) {
            throw new IllegalStateException();
        }
        String requestKey = channelId + "#" + streamId;
        requests.put(requestKey, request);
        // do we need the stream ID here in HTTP 1 header?
        request.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), Integer.toString(streamId));
        // add matching cookies from box (previous requests) and new cookies from request builder
        Collection<Cookie> cookies = new ArrayList<>();
        cookies.addAll(matchCookiesFromBox(request));
        cookies.addAll(matchCookies(request));
        if (!cookies.isEmpty()) {
            request.headers().set(HttpHeaderNames.COOKIE, ClientCookieEncoder.STRICT.encode(cookies));
        }
        // add stream-id and cookie headers
        fullHttpRequest.headers().set(request.headers());
        if (request.content() == null && !request.getBodyData().isEmpty()) {
            try {
                httpPostRequestEncoder =
                        new HttpPostRequestEncoder(httpDataFactory, fullHttpRequest, true);
                httpPostRequestEncoder.setBodyHttpDatas(request.getBodyData());
                httpPostRequestEncoder.finalizeRequest();
            } catch (HttpPostRequestEncoder.ErrorDataEncoderException e) {
                throw new IOException(e);
            }
        }
        if (channel.isWritable()) {
            channel.write(fullHttpRequest);
            if (httpPostRequestEncoder != null && httpPostRequestEncoder.isChunked()) {
                channel.write(httpPostRequestEncoder);
            }
            channel.flush();
            if (httpPostRequestEncoder != null) {
                httpPostRequestEncoder.cleanFiles();
            }
            client.getRequestCounter().incrementAndGet();
        }
        return this;
    }

    @Override
    public void responseReceived(Channel channel, Integer streamId, FullHttpResponse fullHttpResponse) {
        if (throwable != null) {
            logger.log(Level.WARNING, "throwable not null", throwable);
            return;
        }
        if (requests.isEmpty()) {
            logger.log(Level.WARNING, "no request present for responding");
            return;
        }
        String requestKey = requests.lastKey();
        Request request;
        DefaultHttpResponse httpResponse = null;
        try {
            // streamID is expected to be null, last request on memory
            // is expected to be current, remove request from memory
            request = requests.get(requestKey);
            if (request != null) {
                for (String cookieString : fullHttpResponse.headers().getAll(HttpHeaderNames.SET_COOKIE)) {
                    Cookie cookie = ClientCookieDecoder.STRICT.decode(cookieString);
                    addCookie(cookie);
                }
                httpResponse = new DefaultHttpResponse(httpAddress, fullHttpResponse, getCookieBox());
                request.onResponse(httpResponse);
                client.getResponseCounter().incrementAndGet();
            } else {
                logger.log(Level.WARNING, "unable to find request for response");
            }
            // check for retry / continue
            try {
                Request retryRequest = retry(request, httpResponse);
                if (retryRequest != null) {
                    // retry transport, wait for completion
                    client.retry(this, retryRequest);
                } else {
                    Request continueRequest = continuation(request, httpResponse);
                    if (continueRequest != null) {
                        // continue with new transport, synchronous call here,
                        // wait for completion
                        client.continuation(this, continueRequest);
                    }
                }
            } catch (URLSyntaxException | IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
            // acknowledge success, if possible
            String channelId = channel.id().toString();
            Flow flow = flowMap.get(channelId);
            if (flow != null) {
                Integer lastKey = flow.lastKey();
                if (lastKey != null) {
                    CompletableFuture<Boolean> promise = flow.get(lastKey);
                    if (promise != null) {
                        promise.complete(true);
                    }
                }
            }
        } finally {
            if (requestKey != null) {
                requests.remove(requestKey);
            }
            if (httpResponse != null) {
                httpResponse.release();
            }
        }
    }

    @Override
    public void settingsReceived(Http2Settings http2Settings) {
    }

    @Override
    public void waitForSettings() {
    }

    @Override
    public void pushPromiseReceived(Channel channel, Integer streamId,
                                    Integer promisedStreamId, Http2Headers headers) {
    }

    @Override
    protected String getRequestKey(String channelId, Integer streamId) {
        return requests.isEmpty() ? null : requests.lastKey();
    }
}
