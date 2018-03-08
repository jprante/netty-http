package org.xbib.netty.http.client.test;

import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PooledClientTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testPooledClientWithSingleNode() throws IOException {
        int loop = 10;
        HttpAddress httpAddress = HttpAddress.http1("xbib.org", 80);
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolSecure(httpAddress.isSecure())
                .setPoolNodeConnectionLimit(16)
                .build();
        AtomicInteger count = new AtomicInteger();
        try {
            int threads = 16;
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                executorService.submit(() -> {
                    try {
                        logger.log(Level.INFO, "starting " + Thread.currentThread());
                        for (int i = 0; i < loop; i++) {
                            Request request = Request.get()
                                    .url("http://xbib.org/repository/")
                                    .setVersion("HTTP/1.1")
                                    .build()
                                    .setResponseListener(fullHttpResponse -> {
                                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                                        //logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                                    });
                            client.pooledExecute(request).get();
                            count.getAndIncrement();
                        }
                        logger.log(Level.INFO, "done " + Thread.currentThread());
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
        logger.log(Level.INFO, "count = " + count.get());
    }
}
