package org.xbib.netty.http.server.test;

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

public class SecureHttp2Test extends TestBase {

    private static final Logger logger = Logger.getLogger(SecureHttp2Test.class.getName());

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    public void testSimpleSecureHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder()
                .setJdkSslProvider()
                .setSelfCert()
                .bind(httpAddress)
                .build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write(200, "text/plain", request.getRequest().content().retain()));
        server.accept();
        Client client = Client.builder()
                .setJdkSslProvider()
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            logger.log(Level.INFO, "response listener: headers = " + fullHttpResponse.headers().entries() +
                    " response body = " + response);
            counter.incrementAndGet();
        };
        try {
            Transport transport = client.newTransport(httpAddress);
            String payload = Integer.toString(0) + "/" + Integer.toString(0);
            Request request = Request.get()
                    .setVersion("HTTP/2.0")
                    .uri("/")
                    //.url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .build()
                    .setResponseListener(responseListener);
            transport.execute(request);
            transport.get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter = " + counter.get());
        assertEquals(1, counter.get());
    }

    @Test
    public void testPooledSecureHttp2() throws Exception {
        int loop = 4096;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder()
                .setJdkSslProvider()
                .setSelfCert()
                .bind(httpAddress)
                .build();
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
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            //logger.log(Level.INFO, "response listener: headers = " + fullHttpResponse.headers().entries() +
            //        " response body = " + response);
            counter.incrementAndGet();
        };
        try {
            // single transport, single thread
            Transport transport = client.newTransport();
            for (int i = 0; i < loop; i++) {
                String payload = Integer.toString(0) + "/" + Integer.toString(i);
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
        logger.log(Level.INFO, "counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    public void testMultithreadPooledSecureHttp2() throws Exception {
        int threads = 4;
        int loop = 4 * 1024;
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
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
        // a HTTP/2 listener always receives responses out-of-order
        final ResponseListener responseListener = fullHttpResponse -> {
            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
            //logger.log(Level.INFO, "response listener: headers = " + fullHttpResponse.headers().entries() +
            //        " response body = " + response);
            counter.incrementAndGet();
        };
        try {
            // note: for HTTP/2 only, we can use a single shared transport
            final Transport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = Integer.toString(t) + "/" + Integer.toString(i);
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
            boolean terminated = executorService.awaitTermination(30, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(30, TimeUnit.SECONDS);
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "expected=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop , counter.get());
    }
}
