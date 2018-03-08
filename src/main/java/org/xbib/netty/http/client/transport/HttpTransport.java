package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.HttpResponseListener;

import java.io.IOException;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpTransport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(HttpTransport.class.getName());

    private final AtomicInteger sequentialCounter;

    private SortedMap<Integer, CompletableFuture<Boolean>> sequentialPromiseMap;

    public HttpTransport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
        this.sequentialCounter = new AtomicInteger();
        this.sequentialPromiseMap = new ConcurrentSkipListMap<>();
    }

    @Override
    public Integer nextStream() {
        Integer streamId = sequentialCounter.getAndIncrement();
        if (streamId == Integer.MIN_VALUE) {
            // reset if overflow, Java wraps atomic integers to Integer.MIN_VALUE
            sequentialCounter.set(0);
            streamId = 0;
        }
        sequentialPromiseMap.put(streamId, new CompletableFuture<>());
        return streamId;
    }

    @Override
    public void settingsReceived(Channel channel, Http2Settings http2Settings) {
    }

    @Override
    public void awaitSettings() {
    }

    @Override
    public void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse) {
        Request request = fromStreamId(streamId);
        if (request != null) {
            HttpResponseListener responseListener = request.getResponseListener();
            if (responseListener != null) {
                responseListener.onResponse(fullHttpResponse);
            }
        }
        try {
            Request retryRequest = retry(request, fullHttpResponse);
            if (retryRequest != null) {
                // retry transport, wait for completion
                client.retry(this, retryRequest);
                retryRequest.close();
            } else {
                Request continueRequest = continuation(request, fullHttpResponse);
                if (continueRequest != null) {
                    // continue with new transport, synchronous call here, wait for completion
                    client.continuation(this, continueRequest);
                    continueRequest.close();
                }
            }
        } catch (URLSyntaxException | IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        if (!sequentialPromiseMap.isEmpty()) {
            CompletableFuture<Boolean> promise = sequentialPromiseMap.get(sequentialPromiseMap.firstKey());
            if (promise != null) {
                promise.complete(true);
            }
        }
    }

    @Override
    public void pushPromiseReceived(Integer streamId, Integer promisedStreamId, Http2Headers headers) {
    }

    @Override
    public void awaitResponse(Integer streamId) throws IOException {
        if (streamId == null) {
            return;
        }
        if (throwable != null) {
            return;
        }
        CompletableFuture<Boolean> promise = sequentialPromiseMap.get(streamId);
        if (promise != null) {
            try {
                promise.get(client.getClientConfig().getReadTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                this.throwable = e;
                throw new IOException(e);
            } finally {
                sequentialPromiseMap.remove(streamId);
            }
        }
    }

    @Override
    public Transport get() {
        try {
            for (Integer streamId : sequentialPromiseMap.keySet()) {
                awaitResponse(streamId);
                client.releaseChannel(channel);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } finally {
            sequentialPromiseMap.clear();
        }
        return this;
    }

    @Override
    public  void success() {
        for (CompletableFuture<Boolean> promise : sequentialPromiseMap.values()) {
            promise.complete(true);
        }
    }

    @Override
    public void fail(Throwable throwable) {
        this.throwable = throwable;
        for (CompletableFuture<Boolean> promise : sequentialPromiseMap.values()) {
            promise.completeExceptionally(throwable);
        }
    }
}
