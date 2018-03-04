package org.xbib.netty.http.client.test;

import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.transport.Transport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

@Ignore
public class ElasticsearchTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger(ElasticsearchTest.class.getName());

    @Test
    public void testElasticsearchCreateDocument() throws IOException {
        Client client = new Client();
        try {
            Request request = Request.put().url("http://localhost:9200/test/test/1")
                    .json("{\"text\":\"Hello World\"}")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    });
            client.execute(request);
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testElasticsearchMatchQuery() throws IOException {
        Client client = new Client();
        try {
            Request request = Request.post().url("http://localhost:9200/test/_search")
                    .json("{\"query\":{\"match\":{\"text\":\"Hello World\"}}}")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testElasticsearchConcurrent() throws IOException {
        Client client = Client.builder().setReadTimeoutMillis(20000).build();
        int max = 1000;
        try {
            List<Request> queries = new ArrayList<>();
            for (int i = 0; i < max; i++) {
                queries.add(newRequest());
            }
            Transport transport = client.execute(queries.get(0)).get();
            for (int i = 1; i < max; i++) {
                transport.execute(queries.get(i)).get();
            }
        } finally {
            client.shutdownGracefully();
            logger.log(Level.INFO, "count=" + count);
        }
        assertEquals(max, count.get());
    }

    private Request newRequest() {
        return Request.post()
                .url("http://localhost:9200/test/_search")
                .json("{\"query\":{\"match\":{\"text\":\"Hello World\"}}}")
                .addHeader("connection", "keep-alive")
                .build()
                .setResponseListener(fullHttpResponse ->
                    logger.log(Level.FINE, "status = " + fullHttpResponse.status() +
                            " counter = " + count.incrementAndGet() +
                            " response body = " + fullHttpResponse.content().toString(StandardCharsets.UTF_8)));
    }

    private final AtomicInteger count = new AtomicInteger();
}
