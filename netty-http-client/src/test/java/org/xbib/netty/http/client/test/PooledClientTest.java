package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
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
        int threads = Runtime.getRuntime().availableProcessors();
        URL url = URL.from("https://fl-test.hbz-nrw.de/app/fl");
        HttpAddress httpAddress = HttpAddress.of(url, HttpVersion.valueOf("HTTP/2.0"));
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger count = new AtomicInteger();
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                executorService.submit(() -> {
                    try {
                        logger.log(Level.INFO, "starting " + Thread.currentThread());
                        for (int i = 0; i < loop; i++) {
                            Request request = Request.get()
                                    .url(url.toString())
                                    .setVersion(httpAddress.getVersion())
                                    //.setTimeoutInMillis(25000L)
                                    .build()
                                    .setResponseListener(fullHttpResponse -> {
                                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                                        //logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                                        count.getAndIncrement();
                                    });
                            client.pooledExecute(request).get();
                        }
                        logger.log(Level.INFO, "done " + Thread.currentThread());
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);
        } catch (Throwable e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } finally {
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "count = " + count.get());
    }
}
