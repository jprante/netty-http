package org.xbib.netty.http.server.test.http2;

import io.netty.handler.codec.http.HttpResponseStatus;
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
    void testSimpleCleartextHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            String payload = 0 + "/" + 0;
            Request request = Request.get().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .setResponseListener(responseListener)
                    .build();
            ClientTransport transport = client.newTransport(httpAddress);
            transport.execute(request);
            if (transport.isFailed()) {
                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
            }
            transport.get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + 1 + " counter=" + counter.get());
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledClearTextHttp2() throws Exception {
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(4)
                .build();
        final AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            ClientTransport transport = client.newTransport();
            for (int i = 0; i < loop; i++) {
                String payload = 0 + "/" + i;
                Request request = Request.get().setVersion("HTTP/2.0")
                        .url(server.getServerConfig().getAddress().base())
                        .content(payload, "text/plain")
                        .setResponseListener(responseListener)
                        .build();
                transport.execute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
            }
            transport.get(30L, TimeUnit.SECONDS);
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledClearTextHttp2() throws Exception {
        int threads = 4;
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
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
            // note: for HTTP/2 only, we can use a single shared transport
            final ClientTransport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            Request request = Request.get()
                                    .setVersion("HTTP/2.0")
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .setResponseListener(responseListener)
                                    .build();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            Thread.sleep(5000L);
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(30L, TimeUnit.SECONDS);
            executorService.shutdownNow();
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting 30s for transport to complete");
            transport.get(30L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "transport complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "client requests = " + client.getRequestCounter() +
                " client responses = " + client.getResponseCounter());
        logger.log(Level.INFO, "expected=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop , counter.get());
    }

    @Test
    void testTwoPooledClearTextHttp2() throws Exception {
        int threads = 4;
        int loop = 1024;
        HttpAddress httpAddress1 = HttpAddress.http2("localhost", 8008);
        AtomicInteger counter1 = new AtomicInteger();
        HttpServerDomain domain1 = HttpServerDomain.builder(httpAddress1)
                .singleEndpoint("/", (request, response) -> {
                    response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                            .write(request.getContent().toString(StandardCharsets.UTF_8));
                    counter1.incrementAndGet();
                })
                .build();
        Server server1 = Server.builder(domain1)
                .build();
        server1.accept();
        HttpAddress httpAddress2 = HttpAddress.http2("localhost", 8009);
        AtomicInteger counter2 = new AtomicInteger();
        HttpServerDomain domain2 = HttpServerDomain.builder(httpAddress2)
                .singleEndpoint("/", (request, response) -> {
                    response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                            .write(request.getContent().toString(StandardCharsets.UTF_8));
                    counter2.incrementAndGet();
                })
                .build();
        Server server2 = Server.builder(domain2)
                .build();
        server2.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress1)
                .addPoolNode(httpAddress2)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            // note: for HTTP/2 only, we can use a single shared transport
            final ClientTransport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            // note  that we do not set url() in the request
                            Request request = Request.get()
                                    .setVersion("HTTP/2.0")
                                    .content(payload, "text/plain")
                                    .setResponseListener(responseListener)
                                    .build();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            Thread.sleep(5000L);
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(30L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(30L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "transport complete");
        } finally {
            server1.shutdownGracefully();
            server2.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "client requests = " + client.getRequestCounter() +
                " client responses = " + client.getResponseCounter());
        logger.log(Level.INFO, "counter1=" + counter1.get() + " counter2=" + counter2.get());
        logger.log(Level.INFO, "expecting=" + threads * loop + " counter=" + counter.get());
        assertEquals(threads * loop, counter.get());
    }
}
