package org.xbib.netty.http.client.test.http1;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class Http1Test {

    private static final Logger logger = Logger.getLogger(Http1Test.class.getName());

    @Test
    void testHttp1() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            Request request = Request.get().url("http://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.FINE,
                            "got response: " + resp.getHeaders() +
                            resp.getBodyAsString(StandardCharsets.UTF_8) +
                            " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testSequentialRequests() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.get().url("http://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request1).get();
            Request request2 = Request.get().url("http://google.com").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request2).get();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testParallelRequests() throws IOException {
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .url("http://xbib.org").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getHeaders() + " status=" +resp.getStatus()))
                    .build();
            Request request2 = Request.builder(HttpMethod.GET)
                    .url("http://xbib.org").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getHeaders() + " status=" +resp.getStatus()))
                    .build();
            for (int i = 0; i < 10; i++) {
                client.execute(request1);
                client.execute(request2);
            }

        } finally {
            client.shutdownGracefully();
        }
    }
}