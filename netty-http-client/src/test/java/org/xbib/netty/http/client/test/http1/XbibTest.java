package org.xbib.netty.http.client.test.http1;

import io.netty.handler.proxy.HttpProxyHandler;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpResponse;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

class XbibTest {

    private static final Logger logger = Logger.getLogger(XbibTest.class.getName());

    @Test
    void testXbibOrgWithDefaults() throws IOException {
        Client client = Client.builder().build();
        try {
            Request request = Request.get().url("https://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.INFO, "status = " + resp.getStatus() +
                                " response = " + resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .setTimeoutListener(req -> logger.log(Level.WARNING, "timeout!"))
                    .setExceptionListener(throwable -> logger.log(Level.SEVERE, throwable.getMessage(), throwable))
                    .build();
            client.execute(request).close();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testXbibOrgWithCompletableFuture() throws IOException {
        Client httpClient = Client.builder()
                .setTcpNodelay(true)
                .build();
        try {
            final Function<HttpResponse, String> stringFunction =
                    response -> response.getBodyAsString(StandardCharsets.UTF_8);
            Request request = Request.get().url("https://xbib.org")
                    .build();
            final CompletableFuture<String> completableFuture = httpClient.execute(request, stringFunction)
                    .exceptionally(Throwable::getMessage)
                    .thenCompose(content -> {
                        try {
                            return httpClient.execute(Request.post()
                                    .url("http://google.de")
                                    .addParameter("query", content.substring(0, 15))
                                    .build(), stringFunction);
                        } catch (IOException e) {
                            logger.log(Level.WARNING, e.getMessage(), e);
                            return null;
                        }
                    });
            String result = completableFuture.join();
            logger.info("result = " + result);
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    void testXbibOrgWithProxy() throws IOException {
        Client httpClient = Client.builder()
                .setHttpProxyHandler(new HttpProxyHandler(new InetSocketAddress("80.241.223.251", 8080)))
                .setConnectTimeoutMillis(30000)
                .setReadTimeoutMillis(30000)
                .build();
        try {
            httpClient.execute(Request.get()
                    .url("https://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.INFO, "status = " + resp.getStatus() +
                            " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build())
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    void testXbibOrgWithVeryShortReadTimeout() throws IOException {
        Client httpClient = Client.builder()
                .build();
        try {
            httpClient.execute(Request.get()
                    .url("https://xbib.org")
                    .setTimeoutInMillis(10)
                    .setResponseListener(resp ->
                            logger.log(Level.INFO, "status = " + resp.getStatus() +
                                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build())
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    void testXbibTwoSequentialRequests() throws IOException {
        Client httpClient = new Client();
        try {
            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .url("https://xbib.org")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "status = " + resp.getStatus() +
                                " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
                    })
                    .build())
                    .get();
            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .url("https://xbib.org")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "status = " + resp.getStatus() +
                                " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
                    })
                    .build())
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }
}
