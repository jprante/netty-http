package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http1Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger(Http1Test.class.getName());

    @Test
    public void testHttp1() throws Exception {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request = Request.get().url("http://xbib.org").build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));
            client.execute(request).get();
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testParallelRequests() throws IOException {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .url("http://xbib.org").setVersion("HTTP/1.1")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));
            Request request2 = Request.builder(HttpMethod.GET)
                    .url("http://xbib.org").setVersion("HTTP/1.1")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));

            for (int i = 0; i < 10; i++) {
                client.execute(request1);
                client.execute(request2);
            }

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testSequentialRequests() throws Exception {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request1 = Request.get().url("http://xbib.org").build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.content().toString(StandardCharsets.UTF_8)));
            client.execute(request1).get();

            Request request2 = Request.get().url("http://google.com").setVersion("HTTP/1.1").build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.content().toString(StandardCharsets.UTF_8)));
            client.execute(request2).get();
        } finally {
            client.shutdown();
        }
    }
}
