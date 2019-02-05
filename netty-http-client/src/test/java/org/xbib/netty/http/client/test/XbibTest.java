package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.proxy.HttpProxyHandler;
import org.junit.Test;
import org.xbib.TestBase;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XbibTest extends TestBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testXbibOrgWithDefaults() throws IOException {
        Client client = new Client();
        try {
            Request request = Request.get().url("http://xbib.org")
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
    public void testXbibOrgWithCompletableFuture() throws IOException {
        Client httpClient = Client.builder()
                .setTcpNodelay(true)
                .build();
        try {
            final Function<FullHttpResponse, String> httpResponseStringFunction =
                    response -> response.content().toString(StandardCharsets.UTF_8);
            Request request = Request.get().url("http://xbib.org")
                    .build();
            final CompletableFuture<String> completableFuture = httpClient.execute(request, httpResponseStringFunction)
                    .exceptionally(Throwable::getMessage)
                    .thenCompose(content -> {
                        try {
                            return httpClient.execute(Request.post()
                                    .url("http://google.de")
                                    .addParameter("query", content.substring(0, 15))
                                    .build(), httpResponseStringFunction);
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
    public void testXbibOrgWithProxy() throws IOException {
        Client httpClient = Client.builder()
                .setHttpProxyHandler(new HttpProxyHandler(new InetSocketAddress("80.241.223.251", 8080)))
                .setConnectTimeoutMillis(30000)
                .setReadTimeoutMillis(30000)
                .build();
        try {
            httpClient.execute(Request.get()
                    .url("http://xbib.org")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    }))
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    public void testXbibOrgWithVeryShortReadTimeout() throws IOException {
        Client httpClient = Client.builder()
                .build();
        try {
            httpClient.execute(Request.get()
                    .url("http://xbib.org")
                    .setTimeoutInMillis(10)
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    }))
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    public void testXbibTwoSequentialRequests() throws IOException {
        Client httpClient = new Client();
        try {
            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .url("http://xbib.org")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    }))
                    .get();

            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .url("http://xbib.org")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    }))
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }
}
