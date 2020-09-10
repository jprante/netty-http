package org.xbib.netty.http.server.protocol.http1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;

import java.nio.charset.StandardCharsets;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements HTTP pipelining ordering, ensuring that responses are completely served in the same order as their
 * corresponding requests.
 *
 * Based on https://github.com/typesafehub/netty-http-pipelining - which uses netty 3
 */
public class HttpPipeliningHandler extends ChannelDuplexHandler {

    private final Logger logger = Logger.getLogger(HttpPipeliningHandler.class.getName());

    private final int pipelineCapacity;

    private final Lock lock;

    private final Queue<HttpPipelinedResponse> httpPipelinedResponses;

    private final AtomicInteger requestCounter;

    private final AtomicInteger writtenRequests;

    /**
     * @param pipelineCapacity the maximum number of channel events that will be retained prior to aborting the channel
     *                      connection. This is required as events cannot queue up indefinitely; we would run out of
     *                      memory if this was the case.
     */
    public HttpPipeliningHandler(int pipelineCapacity) {
        this.pipelineCapacity = pipelineCapacity;
        this.lock = new ReentrantLock();
        this.httpPipelinedResponses = new PriorityQueue<>(3);
        this.requestCounter = new AtomicInteger();
        this.writtenRequests = new AtomicInteger();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof LastHttpContent) {
            super.channelRead(ctx, new HttpPipelinedRequest((LastHttpContent) msg, requestCounter.getAndIncrement()));
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpPipelinedResponse) {
            boolean channelShouldClose = false;
            lock.lock();
            try {
                if (httpPipelinedResponses.size() < pipelineCapacity) {
                    HttpPipelinedResponse currentEvent = (HttpPipelinedResponse) msg;
                    httpPipelinedResponses.add(currentEvent);
                    while (!httpPipelinedResponses.isEmpty()) {
                        HttpPipelinedResponse queuedPipelinedResponse = httpPipelinedResponses.peek();
                        if (queuedPipelinedResponse.getSequenceId() != writtenRequests.get()) {
                            break;
                        }
                        httpPipelinedResponses.remove();
                        super.write(ctx, queuedPipelinedResponse.getResponse(), queuedPipelinedResponse.getPromise());
                        writtenRequests.getAndIncrement();
                    }
                } else {
                    channelShouldClose = true;
                }
            } finally {
                lock.unlock();
            }
            if (channelShouldClose) {
                ctx.close();
            }
        } else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.log(Level.SEVERE, cause.getMessage(), cause);
        ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.INTERNAL_SERVER_ERROR,
                Unpooled.copiedBuffer(cause.getMessage().getBytes(StandardCharsets.UTF_8))));
    }
}
