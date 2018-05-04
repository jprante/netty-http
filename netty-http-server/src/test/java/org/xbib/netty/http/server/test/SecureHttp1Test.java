package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpVersion;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.TestBase;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class SecureHttp1Test extends TestBase {

    private static final Logger logger = Logger.getLogger(SecureHttp1Test.class.getName());

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void testSimpleSecureHttp1() throws Exception {
        Server server = Server.builder()
                .setJdkSslProvider()
                .setSelfCert()
                .bind(HttpAddress.secureHttp1("localhost", 8143))
                .build();
        Client client = Client.builder()
                .setJdkSslProvider()
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            //logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
            counter.getAndIncrement();
        };
        try {
            server.getDefaultVirtualServer().addContext("/", (request, response) ->
                    response.write(200, "text/plain", request.getRequest().content().retain()));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .build()
                    .setResponseListener(responseListener);
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter=" + counter.get());
        assertEquals(1, counter.get());
    }

    @Test
    public void testPooledSecureHttp1() throws Exception {
        int loop = 4096;
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder()
                .setJdkSslProvider()
                .setSelfCert()
                .bind(httpAddress).build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write(200, "text/plain", request.getRequest().content().retain()));
        server.accept();
        Client client = Client.builder()
                .setJdkSslProvider()
                .trustInsecure()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(2)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            //logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
            counter.incrementAndGet();
        };
        try {
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
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
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    public void testMultithreadPooledSecureHttp1() throws Exception {
        int threads = 4;
        int loop = 4 * 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder()
                .setJdkSslProvider()
                .setSelfCert()
                .bind(httpAddress)
                .build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write(200, "text/plain", request.getRequest().content().retain())
        );
        server.accept();
        Client client = Client.builder()
                .setJdkSslProvider()
                .trustInsecure()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            //logger.log(Level.INFO, "response listener: headers = " + fullHttpResponse.headers().entries() +
            //        " response body = " + response);
            counter.incrementAndGet();
        };
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = Integer.toString(t) + "/" + Integer.toString(i);
                            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .build()
                                    .setResponseListener(responseListener);
                            // note: a new transport is created per execution
                            final Transport transport = client.newTransport();
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
            boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated);
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop , counter.get());
    }
}
