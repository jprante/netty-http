package org.xbib.netty.http.server.internal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayDeque;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * HTTP 1 object encoder.
 */
public final class Http1ObjectEncoder extends HttpObjectEncoder {

    private static final Logger logger = Logger.getLogger(Http1ObjectEncoder.class.getName());

    /**
     * The map which maps a request ID to its related pending response.
     */
    private final IntObjectMap<PendingWrites> pendingWrites = new IntObjectHashMap<>();
    /**
     * The ID of the request which is at its turn to send a response.
     */
    private int currentId = 1;
    /**
     * The minimum ID of the request whose stream has been closed/reset.
     */
    private int minClosedId = Integer.MAX_VALUE;
    /**
     * The maximum known ID with pending writes.
     */
    private int maxIdWithPendingWrites = Integer.MIN_VALUE;

    @Override
    protected ChannelFuture doWriteHeaders(ChannelHandlerContext ctx, int id, int streamId,
                                           HttpHeaders headers, HttpResponseStatus status, boolean endStream) {
        if (id >= minClosedId) {
            return ctx.newFailedFuture(new ClosedSessionException());
        }
        try {
            return write(ctx, id, new DefaultHttpResponse(HttpVersion.HTTP_1_1, status, headers), endStream);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    @Override
    protected ChannelFuture doWriteData(ChannelHandlerContext ctx, int id, int streamId, ByteBuf buf, boolean endStream) {
        if (id >= minClosedId) {
            return ctx.newFailedFuture(new ClosedSessionException());
        }
        try {
            final HttpContent content;
            if (endStream) {
                content = new DefaultLastHttpContent(buf);
            } else {
                content = new DefaultHttpContent(buf);
            }
            return write(ctx, id, content, endStream);
        } catch (Throwable t) {
            return ctx.newFailedFuture(t);
        }
    }

    private ChannelFuture write(ChannelHandlerContext ctx, int id, HttpObject obj, boolean endStream) {
        if (id < currentId) {
            return ctx.newFailedFuture(new ClosedSessionException());
        }
        final PendingWrites currentPendingWrites = pendingWrites.get(id);
        if (id == currentId) {
            if (currentPendingWrites != null) {
                pendingWrites.remove(id);
                flushPendingWrites(ctx, currentPendingWrites);
            }
            final ChannelFuture future = ctx.write(obj);
            if (endStream) {
                currentId++;
                for (;;) {
                    final PendingWrites nextPendingWrites = pendingWrites.get(currentId);
                    if (nextPendingWrites == null) {
                        break;
                    }
                    flushPendingWrites(ctx, nextPendingWrites);
                    if (!nextPendingWrites.isEndOfStream()) {
                        break;
                    }
                    pendingWrites.remove(currentId);
                    currentId++;
                }
            }
            ctx.flush();
            return future;
        } else {
            final ChannelPromise promise = ctx.newPromise();
            final Entry<HttpObject, ChannelPromise> entry = new SimpleImmutableEntry<>(obj, promise);
            if (currentPendingWrites == null) {
                final PendingWrites newPendingWrites = new PendingWrites();
                maxIdWithPendingWrites = Math.max(maxIdWithPendingWrites, id);
                newPendingWrites.add(entry);
                pendingWrites.put(id, newPendingWrites);
            } else {
                currentPendingWrites.add(entry);
                if (endStream) {
                    currentPendingWrites.setEndOfStream();
                }
            }
            return promise;
        }
    }

    private static void flushPendingWrites(ChannelHandlerContext ctx, PendingWrites pendingWrites) {
        while (true) {
            final Entry<HttpObject, ChannelPromise> e = pendingWrites.poll();
            if (e == null) {
                break;
            }
            ctx.write(e.getKey(), e.getValue());
        }
    }

    @Override
    protected ChannelFuture doWriteReset(ChannelHandlerContext ctx, int id, int streamId, Http2Error error) {
        minClosedId = Math.min(minClosedId, id);
        for (int i = minClosedId; i <= maxIdWithPendingWrites; i++) {
            final PendingWrites pendingWrites = this.pendingWrites.remove(i);
            while (true) {
                final Entry<HttpObject, ChannelPromise> e = pendingWrites.poll();
                if (e == null) {
                    break;
                }
                e.getValue().tryFailure(new ClosedSessionException());
            }
        }
        final ChannelFuture f = ctx.write(Unpooled.EMPTY_BUFFER);
        if (currentId >= minClosedId) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
        return f;
    }

    @Override
    protected void doClose() {
        if (pendingWrites.isEmpty()) {
            return;
        }
        ClosedSessionException cause = new ClosedSessionException();
        for (Queue<Entry<HttpObject, ChannelPromise>> queue : pendingWrites.values()) {
            while (true) {
                final Entry<HttpObject, ChannelPromise> e = queue.poll();
                if (e == null) {
                    break;
                }
                e.getValue().tryFailure(cause);
            }
        }
        pendingWrites.clear();
    }

    private static final class PendingWrites extends ArrayDeque<Entry<HttpObject, ChannelPromise>> {

        private boolean endOfStream;

        PendingWrites() {
            super(4);
        }

        boolean isEndOfStream() {
            return endOfStream;
        }

        void setEndOfStream() {
            endOfStream = true;
        }
    }
}
