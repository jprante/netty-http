package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.ServerResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpTestExtension.class)
class CleartextHttp1Test {

    private static final Logger logger = Logger.getLogger(CleartextHttp1Test.class.getName());

    @Test
    void testSimpleClearTextHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().retain()))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                logger.log(Level.INFO, resp.getBodyAsString(StandardCharsets.UTF_8));
                counter.incrementAndGet();
            }
        };
        try {
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .content("Hello world", "text/plain")
                    .build()
                    .setResponseListener(responseListener);
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledClearTextHttp1() throws Exception {
        int loop = 1000;
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().retain()))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(2)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                logger.log(Level.INFO, resp.getBodyAsString(StandardCharsets.UTF_8));
                counter.incrementAndGet();
            }
        };
        try {
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion("HTTP/1.1")
                        .url(server.getServerConfig().getAddress().base())
                        .content(Integer.toString(i), "text/plain")
                        .build()
                        .setResponseListener(responseListener);
                Transport transport = client.newTransport();
                transport.execute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
                transport.get();
            }
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadedPooledClearTextHttp1() throws Exception {
        int threads = 8;
        int loop = 1000;
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().retain()))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                logger.log(Level.FINE, resp.getBodyAsString(StandardCharsets.UTF_8));
                counter.incrementAndGet();
            }
        };
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .build()
                                    .setResponseListener(responseListener);
                            // note: a new transport is created per execution
                            Transport transport = client.newTransport();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, "transport failed: " + transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                            transport.get();
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(threads * loop, counter.get());
    }
}
