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
class EncryptedTest {

    private static final Logger logger = Logger.getLogger(EncryptedTest.class.getName());

    @Test
    void testSimpleSecureHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
                        .write(request.getContent().retain()))
                .build())
                .build();
        Client client = Client.builder()
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> counter.getAndIncrement();
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .setResponseListener(responseListener)
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledSecureHttp1() throws Exception {
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build())
                .build();
        server.accept();
        Client client = Client.builder()
                .trustInsecure()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(4)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> counter.incrementAndGet();
        try {
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
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
    void testMultithreadPooledSecureHttp1() throws Exception {
        int threads = 4;
        int loop = 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                                .setContentType("text/plain").build()
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
        final ResponseListener<HttpResponse> responseListener = resp -> counter.incrementAndGet();
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
                                    .setResponseListener(responseListener)
                                    .build();
                            // note: a new transport is created per execution
                            final ClientTransport transport = client.newTransport();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                            transport.get();
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(30L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated);
        } finally {
            client.shutdownGracefully(30L, TimeUnit.SECONDS);
            server.shutdownGracefully(30L, TimeUnit.SECONDS);
        }
        assertEquals(threads * loop , counter.get());
    }
}
