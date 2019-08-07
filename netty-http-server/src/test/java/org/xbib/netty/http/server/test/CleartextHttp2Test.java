package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.Domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpExtension.class)
class CleartextHttp2Test {

    private static final Logger logger = Logger.getLogger(CleartextHttp2Test.class.getName());

    @Test
    void testSimpleCleartextHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                                response.withStatus(HttpResponseStatus.OK)
                                        .withContentType("text/plain")
                                        .write(request.getContent().retain()))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        ResponseListener<HttpResponse> responseListener = resp -> {
            logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            counter.incrementAndGet();
        };
        try {
            String payload = 0 + "/" + 0;
            Request request = Request.get().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .build()
                    .setResponseListener(responseListener);
            Transport transport = client.newTransport(httpAddress);
            transport.execute(request);
            if (transport.isFailed()) {
                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
            }
            transport.get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledClearTextHttp2() throws Exception {
        int loop = 4096;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                                response.withStatus(HttpResponseStatus.OK)
                                        .withContentType("text/plain")
                                        .write(request.getContent().retain()))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(2)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> counter.incrementAndGet();
        try {
            // single transport, single thread
            Transport transport = client.newTransport();
            for (int i = 0; i < loop; i++) {
                String payload = 0 + "/" + i;
                Request request = Request.get().setVersion("HTTP/2.0")
                        .url(server.getServerConfig().getAddress().base())
                        .content(payload, "text/plain")
                        .build()
                        .setResponseListener(responseListener);
                transport.execute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
            }
            transport.get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledClearTextHttp2() throws Exception {
        int threads = 2;
        int loop = 2 * 1024;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a HTTP/2 listener always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> counter.incrementAndGet();
        try {
            // note: for HTTP/2 only, we can use a single shared transport
            final Transport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            Request request = Request.get().setVersion("HTTP/2.0")
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .build()
                                    .setResponseListener(responseListener);
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(60, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(60, TimeUnit.SECONDS);
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "expected=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop , counter.get());
    }

    @Test
    void testTwoPooledClearTextHttp2() throws Exception {
        int threads = 2;
        int loop = 4 * 1024;

        HttpAddress httpAddress1 = HttpAddress.http2("localhost", 8008);
        AtomicInteger counter1 = new AtomicInteger();
        Domain domain1 = Domain.builder(httpAddress1)
                .singleEndpoint("/", (request, response) -> {
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().toString(StandardCharsets.UTF_8));
                        counter1.incrementAndGet();
                })
                .build();
        Server server1 = Server.builder(domain1).build();
        server1.accept();
        HttpAddress httpAddress2 = HttpAddress.http2("localhost", 8009);
        AtomicInteger counter2 = new AtomicInteger();
        Domain domain2 = Domain.builder(httpAddress2)
                .singleEndpoint("/", (request, response) -> {
                    ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                            request.getContent().toString(StandardCharsets.UTF_8));
                    counter2.incrementAndGet();
                })
                .build();
        Server server2 = Server.builder(domain2).build();
        server2.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress1)
                .addPoolNode(httpAddress2)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> counter.incrementAndGet();
        try {
            // note: for HTTP/2 only, we can use a single shared transport
            final Transport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            Request request = Request.get().setVersion("HTTP/2.0")
                                    .uri("/")
                                    .content(payload, "text/plain")
                                    .build()
                                    .setResponseListener(responseListener);
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(60, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(60, TimeUnit.SECONDS);
        } finally {
            client.shutdownGracefully();
            server1.shutdownGracefully();
            server2.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter1=" + counter1.get() + " counter2=" + counter2.get());
        logger.log(Level.INFO, "expecting=" + threads * loop + " counter=" + counter.get());
        assertEquals(threads * loop, counter.get());
    }
}
