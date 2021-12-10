package org.xbib.netty.http.client.test.http1;

import io.netty.handler.codec.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class GoogleTest {

    private static final Logger logger = Logger.getLogger(GoogleTest.class.getName());

    @Test
    void testHttp1WithTlsV13() throws Exception {
        AtomicBoolean success = new AtomicBoolean();
        Client client = Client.builder()
                .setTransportLayerSecurityProtocols("TLSv1.3")
                .build();
        try {
            Request request = Request.get().url("https://www.google.com/")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "got response: " +
                            resp.getHeaders() + resp.getBodyAsString(StandardCharsets.UTF_8) +
                            " status=" + resp.getStatus());
                        success.set(true);
                    })
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
        assertTrue(success.get());
    }

    @Test
    void testSequentialRequests() throws Exception {
        AtomicBoolean success = new AtomicBoolean();
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.get().url("https://google.com")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "got HTTP 1.1 response: " +
                                resp.getBodyAsString(StandardCharsets.UTF_8));
                        success.set(true);
                    })
                    .build();
            client.execute(request1).get();
        } finally {
            client.shutdownGracefully();
        }
        assertTrue(success.get());
    }

    @Test
    void testParallelRequests() throws IOException {
        AtomicBoolean success1 = new AtomicBoolean();
        AtomicBoolean success2 = new AtomicBoolean();
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .url("https://google.com").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "got response: " +
                                resp.getHeaders() +
                                " status=" + resp.getStatus());
                        success1.set(true);
                    })
                    .build();
            Request request2 = Request.builder(HttpMethod.GET)
                    .url("https://google.com").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "got response: " +
                                resp.getHeaders() +
                                " status=" + resp.getStatus());
                        success2.set(true);
                    })
                    .build();
            for (int i = 0; i < 10; i++) {
                client.execute(request1);
                client.execute(request2);
            }
        } finally {
            client.shutdownGracefully();
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
    }

}
