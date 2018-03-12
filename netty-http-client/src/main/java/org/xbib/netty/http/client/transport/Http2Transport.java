package org.xbib.netty.http.client.transport;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Settings;
import org.xbib.net.URLSyntaxException;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
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

public class Http2Transport extends BaseTransport {

    private static final Logger logger = Logger.getLogger(Http2Transport.class.getName());

    private CompletableFuture<Boolean> settingsPromise;

    private final AtomicInteger streamIdCounter;

    private SortedMap<Integer, CompletableFuture<Boolean>> streamidPromiseMap;

    public Http2Transport(Client client, HttpAddress httpAddress) {
        super(client, httpAddress);
        streamIdCounter = new AtomicInteger(3);
        streamidPromiseMap = new ConcurrentSkipListMap<>();
        settingsPromise = (httpAddress != null /*&& httpAddress.isSecure() */) ||
                (client.hasPooledConnections() && client.getPool().isSecure()) ?
                new CompletableFuture<>() : null;
    }

    @Override
    public Integer nextStream() {
        Integer streamId = streamIdCounter.getAndAdd(2);
        if (streamId == Integer.MIN_VALUE) {
            // reset if overflow, Java wraps atomic integers to Integer.MIN_VALUE
            streamIdCounter.set(3);
            streamId = 3;
        }
        streamidPromiseMap.put(streamId, new CompletableFuture<>());
        return streamId;
    }

    @Override
    public void settingsReceived(Channel channel, Http2Settings http2Settings) {
        if (settingsPromise != null) {
            settingsPromise.complete(true);
        } else {
            logger.log(Level.WARNING, "settings received but no promise present");
        }
    }

    @Override
    public void awaitSettings() {
        if (settingsPromise != null) {
            try {
                logger.log(Level.FINE, "waiting for settings");
                settingsPromise.get(client.getClientConfig().getReadTimeoutMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                logger.log(Level.WARNING, "settings timeout");
                settingsPromise.completeExceptionally(e);
            } catch (InterruptedException | ExecutionException e) {
                settingsPromise.completeExceptionally(e);
            }
        } else {
            logger.log(Level.WARNING, "settings promise is null");
        }
    }

    @Override
    public void responseReceived(Integer streamId, FullHttpResponse fullHttpResponse) {
        if (streamId == null) {
            logger.log(Level.WARNING, "no stream ID, unexpected message received: " + fullHttpResponse);
            return;
        }
        CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
        if (promise == null) {
            logger.log(Level.WARNING, "response received for stream ID " + streamId + " but found no promise");
            return;
        }
        Request request = fromStreamId(streamId);
        if (request != null) {
            HttpResponseListener responseListener = request.getResponseListener();
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
            } catch (URLSyntaxException | IOException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
        promise.complete(true);
    }

    @Override
    public void pushPromiseReceived(Integer streamId, Integer promisedStreamId, Http2Headers headers) {
        streamidPromiseMap.put(promisedStreamId, new CompletableFuture<>());
        requests.put(promisedStreamId, fromStreamId(streamId));
    }

    @Override
    public void awaitResponse(Integer streamId) throws IOException {
        if (streamId == null) {
            return;
        }
        if (throwable != null) {
            return;
        }
        CompletableFuture<Boolean> promise = streamidPromiseMap.get(streamId);
        if (promise != null) {
            try {
                long millis = client.getClientConfig().getReadTimeoutMillis();
                Request request = fromStreamId(streamId);
                if (request != null && request.getTimeoutInMillis() > 0) {
                    millis = request.getTimeoutInMillis();
                }
                promise.get(millis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                this.throwable = e;
                throw new IOException(e);
            } finally {
                streamidPromiseMap.remove(streamId);
            }
        }
    }

    @Override
    public Transport get() {
        for (Integer streamId : streamidPromiseMap.keySet()) {
            try {
                awaitResponse(streamId);
            } catch (IOException e) {
                notifyRequest(streamId, e);
            }
        }
        if (throwable != null) {
            streamidPromiseMap.clear();
        }
        return this;
    }

    @Override
    public  void success() {
        for (CompletableFuture<Boolean> promise : streamidPromiseMap.values()) {
            promise.complete(true);
        }
    }

    /**
     * The underlying network layer failed, not possible to know the request.
     * So we fail all (open) promises.
     * @param throwable the exception
     */
    @Override
    public void fail(Throwable throwable) {
        // fail fast, do not fail more than once
        if (this.throwable != null) {
            return;
        }
        this.throwable = throwable;
        for (CompletableFuture<Boolean> promise : streamidPromiseMap.values()) {
            promise.completeExceptionally(throwable);
        }
    }

    /**
     * Try to notify request about failure.
     * @param streamId stream ID
     * @param throwable the exception
     */
    private void notifyRequest(Integer streamId, Throwable throwable) {
        Request request = fromStreamId(streamId);
        if (request != null && request.getCompletableFuture() != null) {
            request.getCompletableFuture().completeExceptionally(throwable);
        }
    }
}
