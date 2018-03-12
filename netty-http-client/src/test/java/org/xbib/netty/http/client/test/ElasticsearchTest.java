package org.xbib.netty.http.client.test;

import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

@Ignore
public class ElasticsearchTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger(ElasticsearchTest.class.getName());

    @Test
    @Ignore
    public void testElasticsearch() throws IOException {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request = Request.get().url("http://localhost:9200")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    });
            logger.info("request = " + request.toString());
            client.execute(request);
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    @Ignore
    public void testElasticsearchCreateDocument() throws IOException {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request = Request.put().url("http://localhost:9200/test/test/1")
                    .json("{\"text\":\"Hello World\"}")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    });
            logger.info("request = " + request.toString());
            client.execute(request);
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    @Ignore
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

    /**
     * This shows the usage of 4 concurrent pooled connections on 4 threads, querying Elasticsearch.
     * @throws IOException if test fails
     */
    @Test
    public void testElasticsearchPooled() throws IOException {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 9200);
        int limit = 4;
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(limit)
                .build();
        int max = 1000;
        int threads = 4;
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                executorService.submit(() -> {
                    List<Request> queries = new ArrayList<>();
                    for (int i = 0; i < max; i++) {
                        queries.add(newRequest());
                    }
                    try {
                        for (int i = 0; i < max; i++) {
                            client.pooledExecute(queries.get(i)).get();
                        }
                    } catch (IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } finally {
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "count=" + count);
        assertEquals(max * threads, count.get());
    }

    private Request newRequest() {
        return Request.post()
                .url("http://localhost:9200/test/_search")
                .json("{\"query\":{\"match\":{\"text\":\"Hello World\"}}}")
                .addHeader("connection", "keep-alive")
                .build()
                .setResponseListener(fullHttpResponse -> {
                    count.getAndIncrement();
                    if (fullHttpResponse.status().code() != 200) {
                        logger.log(Level.WARNING,"error: " + fullHttpResponse.toString());
                    }
                });
    }

    private final AtomicInteger count = new AtomicInteger();
}
