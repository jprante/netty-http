package org.xbib.netty.http.server.test.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.client.api.ClientTransport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpTestExtension.class)
class CleartextTest {

    private static final Logger logger = Logger.getLogger(CleartextTest.class.getName());

    @Test
    void testSimpleClearTextHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .build();
        AtomicInteger counter = new AtomicInteger();
        try {
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .content("Hello world", "text/plain")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            logger.log(Level.INFO, resp.getBodyAsString(StandardCharsets.UTF_8));
                            counter.incrementAndGet();
                        }
                    })
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledClearTextHttp1() throws Exception {
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(4)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion("HTTP/1.1")
                        .url(server.getServerConfig().getAddress().base())
                        .content(Integer.toString(i), "text/plain")
                        .setResponseListener(responseListener)
                        .build();
                ClientTransport transport = client.newTransport();
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
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledClearTextHttp1() throws Exception {
        int threads = 4;
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain).build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger(0);
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
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
                            Request request = Request.get()
                                    .setVersion(HttpVersion.HTTP_1_1)
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .setResponseListener(responseListener)
                                    .build();
                            // note: in HTTP 1, a new transport is created per execution
                            ClientTransport transport = client.newTransport();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, "transport failed: " + transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                            transport.get(30L, TimeUnit.SECONDS);
                        }
                    } catch (Throwable e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(30L, TimeUnit.SECONDS);
            executorService.shutdownNow();
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            server.shutdownGracefully(30L, TimeUnit.SECONDS);
            client.shutdownGracefully(30L, TimeUnit.SECONDS);
        }
        logger.log(Level.INFO, "client requests = " + client.getRequestCounter() +
                " client responses = " + client.getResponseCounter());
        logger.log(Level.INFO, "expected=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop, counter.get());
    }
}
