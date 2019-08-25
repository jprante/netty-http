package org.xbib.netty.http.client.test.pool;

import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class PooledClientTest {

    private static final Logger logger = Logger.getLogger(PooledClientTest.class.getName());

    @Test
    void testPooledClientWithSingleNode() throws IOException {
        int loop = 10;
        int threads = Runtime.getRuntime().availableProcessors();
        URL url = URL.from("https://fl-test.hbz-nrw.de/");
        HttpAddress httpAddress = HttpAddress.of(url, HttpVersion.valueOf("HTTP/2.0"));
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger count = new AtomicInteger();
        ResponseListener<HttpResponse> responseListener = resp -> {
            String response = resp.getBodyAsString(StandardCharsets.UTF_8);
            count.getAndIncrement();
        };
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                executorService.submit(() -> {
                    try {
                        logger.log(Level.INFO, "starting " + Thread.currentThread());
                        for (int i = 0; i < loop; i++) {
                            Request request = Request.get().setVersion(httpAddress.getVersion())
                                    .url(url.toString())
                                    .build()
                                    .setResponseListener(responseListener);
                            client.newTransport().execute(request).get();
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
