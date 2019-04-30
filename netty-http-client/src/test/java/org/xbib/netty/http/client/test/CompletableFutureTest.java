package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

class CompletableFutureTest {

    private static final Logger logger = Logger.getLogger(CompletableFutureTest.class.getName());

    /**
     * Get some weird content from one URL and post it to another URL, by composing completable futures.
     */
    @Test
    void testComposeCompletableFutures() throws IOException {
        Client client = Client.builder().build();
        try {
            final Function<FullHttpResponse, String> httpResponseStringFunction = response ->
                    response.content().toString(StandardCharsets.UTF_8);
            Request request = Request.get()
                    .url("http://repo.maven.apache.org/maven2/org/xbib/netty-http-client/maven-metadata.xml.sha1")
                    .build();
            CompletableFuture<String> completableFuture = client.execute(request, httpResponseStringFunction)
                    .exceptionally(Throwable::getMessage)
                    .thenCompose(content -> {
                        logger.log(Level.INFO, content);
                        // POST is not allowed, will give a 405. We don't care
                        try {
                            return client.execute(Request.post()
                                    .url("http://google.com/")
                                    .addParameter("query", content)
                                    .build(), httpResponseStringFunction);
                        } catch (IOException e) {
                            logger.log(Level.WARNING, e.getMessage(), e);
                            return null;
                        }
                    });
            String result = completableFuture.join();
            logger.log(Level.INFO, "completablefuture result = " + result);
        } finally {
            client.shutdownGracefully();
        }
    }
}
