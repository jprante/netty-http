package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.proxy.HttpProxyHandler;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.test.LoggingBase;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class XbibTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testXbibOrgWithDefaults() {
        Client client = new Client();
        try {
            Request request = Request.get().setURL("http://xbib.org")
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
    public void testXbibOrgWithCompletableFuture() {
        Client httpClient = Client.builder()
                .setTcpNodelay(true)
                .build();
        try {
            final Function<FullHttpResponse, String> httpResponseStringFunction =
                    response -> response.content().toString(StandardCharsets.UTF_8);
            Request request = Request.get().setURL("http://xbib.org")
                    .build();
            final CompletableFuture<String> completableFuture = httpClient.execute(request, httpResponseStringFunction)
                    .exceptionally(Throwable::getMessage)
                    .thenCompose(content -> httpClient.execute(Request.post()
                            .setURL("http://google.de")
                            .addParam("query", content)
                            .build(), httpResponseStringFunction));
            String result = completableFuture.join();
            logger.info("result = " + result);
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    public void testXbibOrgWithProxy() {
        Client httpClient = Client.builder()
                .setHttpProxyHandler(new HttpProxyHandler(new InetSocketAddress("80.241.223.251", 8080)))
                .setConnectTimeoutMillis(30000)
                .setReadTimeoutMillis(30000)
                .build();
        try {
            httpClient.execute(Request.get()
                    .setURL("http://xbib.org")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    })
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e)))
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    public void testXbibOrgWithVeryShortReadTimeout() {
        Client httpClient = Client.builder()
                .setReadTimeoutMillis(50)
                .build();
        try {
            httpClient.execute(Request.get()
                    .setURL("http://xbib.org")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    })
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e)))
                    .get();
        } finally {
            httpClient.shutdownGracefully();
        }
    }

    @Test
    public void testXbibTwoSequentialRequests() {
        Client httpClient = new Client();
        try {
            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .setURL("http://xbib.org")
                    .build()
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    }))
                    .get();

            httpClient.execute(Request.get()
                    .setVersion("HTTP/1.1")
                    .setURL("http://xbib.org")
                    .build()
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e))
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
