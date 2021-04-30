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
class EncryptedTest {

    private static final Logger logger = Logger.getLogger(EncryptedTest.class.getName());

    @Test
    void testSimpleSecureHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
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
            ClientTransport transport = client.newTransport(httpAddress);
            String payload = 0 + "/" + 0;
            Request request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .setResponseListener(responseListener)
                    .build();
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
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .trustInsecure()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(2)
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
            // single transport, single thread
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
            transport.get(60L, TimeUnit.SECONDS);
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledSecureHttp2() throws Exception {
        int threads = 4;
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
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
            final ClientTransport transport = client.newTransport();
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
                                    .setResponseListener(responseListener)
                                    .build();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(20, TimeUnit.SECONDS);
            executorService.shutdownNow();
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            Thread.sleep(2000L);
            transport.get(20, TimeUnit.SECONDS);
        } finally {
            client.shutdownGracefully(20, TimeUnit.SECONDS);
            server.shutdownGracefully(20, TimeUnit.SECONDS);
        }
        assertEquals(threads * loop , counter.get());
    }
}
