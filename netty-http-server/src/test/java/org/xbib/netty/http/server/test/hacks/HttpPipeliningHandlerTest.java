package org.xbib.netty.http.server.test.hacks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.server.protocol.http1.HttpPipelinedRequest;
import org.xbib.netty.http.server.protocol.http1.HttpPipelinedResponse;
import org.xbib.netty.http.server.protocol.http1.HttpPipeliningHandler;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NettyHttpTestExtension.class)
class HttpPipeliningHandlerTest {

    private static final Logger logger = Logger.getLogger(HttpPipeliningHandlerTest.class.getName());

    private static final Map<String, CountDownLatch> waitingRequests  = new ConcurrentHashMap<>();

    @AfterAll
    void closeResources() {
        for (String url : waitingRequests.keySet()) {
            finishRequest(url);
        }
    }

    @Test
    void testThatPipeliningWorksWithFastSerializedRequests() {
        WorkEmulatorHandler handler = new WorkEmulatorHandler(Executors.newCachedThreadPool());
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpPipeliningHandler(10000),
                handler);
        for (int i = 0; i < 5; i++) {
            embeddedChannel.writeInbound(createHttpRequest("/" + i));
        }
        for (String url : waitingRequests.keySet()) {
            finishRequest(url);
        }
        handler.shutdownExecutorService();
        for (int i = 0; i < 5; i++) {
            assertReadHttpMessageHasContent(embeddedChannel, String.valueOf(i));
        }
        assertTrue(embeddedChannel.isOpen());
    }

    @Test
    void testThatPipeliningWorksWhenSlowRequestsInDifferentOrder() {
        WorkEmulatorHandler handler = new WorkEmulatorHandler(Executors.newCachedThreadPool());
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpPipeliningHandler(10000),
                handler);
        for (int i = 0; i < 5; i++) {
            embeddedChannel.writeInbound(createHttpRequest("/" + i));
        }
        List<String> urls = new ArrayList<>(waitingRequests.keySet());
        Collections.shuffle(urls);
        for (String url : urls) {
            finishRequest(url);
        }
        handler.shutdownExecutorService();
        for (int i = 0; i < 5; i++) {
            assertReadHttpMessageHasContent(embeddedChannel, String.valueOf(i));
        }
        assertTrue(embeddedChannel.isOpen());
    }

    @Test
    void testThatPipeliningWorksWithChunkedRequests() {
        WorkEmulatorHandler handler = new WorkEmulatorHandler(Executors.newCachedThreadPool());
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(new AggregateUrisAndHeadersHandler(),
                new HttpPipeliningHandler(10000), handler);
        DefaultHttpRequest httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/0");
        embeddedChannel.writeInbound(httpRequest);
        embeddedChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        httpRequest = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/1");
        embeddedChannel.writeInbound(httpRequest);
        embeddedChannel.writeInbound(LastHttpContent.EMPTY_LAST_CONTENT);
        finishRequest("1");
        finishRequest("0");
        handler.shutdownExecutorService();
        for (int i = 0; i < 2; i++) {
            assertReadHttpMessageHasContent(embeddedChannel, String.valueOf(i));
        }
        assertTrue(embeddedChannel.isOpen());
    }

    @Test
    void testThatPipeliningClosesConnectionWithTooManyEvents() {
        assertThrows(ClosedChannelException.class, () -> {
            WorkEmulatorHandler handler = new WorkEmulatorHandler(Executors.newCachedThreadPool());
            EmbeddedChannel embeddedChannel = new EmbeddedChannel(new HttpPipeliningHandler(2),
                    handler);
            embeddedChannel.writeInbound(createHttpRequest("/0"));
            embeddedChannel.writeInbound(createHttpRequest("/1"));
            embeddedChannel.writeInbound(createHttpRequest("/2"));
            embeddedChannel.writeInbound(createHttpRequest("/3"));
            finishRequest("1");
            finishRequest("2");
            finishRequest("3");
            finishRequest("0");
            handler.shutdownExecutorService();
            embeddedChannel.writeInbound(createHttpRequest("/"));
        });
    }

    private void assertReadHttpMessageHasContent(EmbeddedChannel embeddedChannel, String expectedContent) {
        FullHttpResponse response = (FullHttpResponse) embeddedChannel.outboundMessages().poll();
        assertNotNull(response);
        assertNotNull(response.content());
        String data = new String(ByteBufUtil.getBytes(response.content()), StandardCharsets.UTF_8);
        assertEquals(expectedContent, data);
    }

    private void finishRequest(String url) {
        waitingRequests.get(url).countDown();
    }

    private FullHttpRequest createHttpRequest(String uri) {
        return new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
    }

    private static class AggregateUrisAndHeadersHandler extends SimpleChannelInboundHandler<HttpRequest> {

        static final Queue<String> STRINGS = new LinkedTransferQueue<>();

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
            STRINGS.add(request.uri());
        }
    }

    private static class WorkEmulatorHandler extends SimpleChannelInboundHandler<HttpPipelinedRequest> {

        private final ExecutorService executorService;

        WorkEmulatorHandler(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpPipelinedRequest pipelinedRequest) {
            QueryStringDecoder decoder;
            if (pipelinedRequest.getRequest() instanceof FullHttpRequest) {
                FullHttpRequest fullHttpRequest = (FullHttpRequest) pipelinedRequest.getRequest();
                decoder = new QueryStringDecoder(fullHttpRequest.uri());
            } else {
                decoder = new QueryStringDecoder(AggregateUrisAndHeadersHandler.STRINGS.poll());
            }
            String uri = decoder.path().replace("/", "");
            ByteBuf content = Unpooled.copiedBuffer(uri, StandardCharsets.UTF_8);
            DefaultFullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK, content);
            httpResponse.headers().add(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
            CountDownLatch latch = new CountDownLatch(1);
            waitingRequests.put(uri, latch);
            executorService.submit(() -> {
                try {
                    latch.await(2, TimeUnit.SECONDS);
                    HttpPipelinedResponse httpPipelinedResponse = new HttpPipelinedResponse(httpResponse,
                            ctx.channel().newPromise(), pipelinedRequest.getSequenceId());
                    ctx.writeAndFlush(httpPipelinedResponse);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            });
        }

        void shutdownExecutorService() {
            if (!executorService.isShutdown()) {
                executorService.shutdown();
                try {
                    executorService.awaitTermination(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.log(Level.WARNING, e.getMessage(), e);
                }
            }
        }
    }
}
