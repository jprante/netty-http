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

@ExtendWith(NettyHttpTestExtension.class)
class SecureHttp2Test {

    private static final Logger logger = Logger.getLogger(SecureHttp2Test.class.getName());

    @Test
    void testSimpleSecureHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(Domain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                                response.withStatus(HttpResponseStatus.OK)
                                        .withContentType("text/plain")
                                        .write(request.getContent().retain()))
                .build())
                .build();
        server.accept();
        Client client = Client.builder()
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> {
            logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            counter.incrementAndGet();
        };
        try {
            Transport transport = client.newTransport(httpAddress);
            String payload = 0 + "/" + 0;
            Request request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .build()
                    .setResponseListener(responseListener);
            transport.execute(request);
            transport.get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledSecureHttp2() throws Exception {
        int loop = 4096;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(Domain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                                response.withStatus(HttpResponseStatus.OK)
                                        .withContentType("text/plain")
                                        .write(request.getContent().retain()))
                .build())
                .build();
        server.accept();
        Client client = Client.builder()
                .trustInsecure()
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
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledSecureHttp2() throws Exception {
        int threads = 4;
        int loop = 4 * 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(Domain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                                response.withStatus(HttpResponseStatus.OK)
                                        .withContentType("text/plain")
                                        .write(request.getContent().retain())
                )
                .build())
                .build();
        server.accept();
        Client client = Client.builder()
                .trustInsecure()
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
        assertEquals(threads * loop , counter.get());
    }
}
