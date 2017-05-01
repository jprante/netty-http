/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.HttpClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class XbibTest {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %2$s %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(Level.ALL);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(Level.ALL);
        }
    }

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testXbibOrgWithDefaults() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setURL("http://xbib.org")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testXbibOrgWithCompletableFuture() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .setTcpNodelay(true)
                .build();

        final Function<FullHttpResponse, String> httpResponseStringFunction =
                response -> response.content().toString(StandardCharsets.UTF_8);

        final CompletableFuture<String> completableFuture = httpClient.prepareGet()
                .setURL("http://index.hbz-nrw.de")
                .execute(httpResponseStringFunction)
                .exceptionally(Throwable::getMessage)
                .thenCompose(content -> httpClient.prepareGet()
                        .setURL("http://google.de/?query=" + content)
                        .execute(httpResponseStringFunction));

        String result = completableFuture.join();

        logger.log(Level.FINE, "completablefuture result = " + result);

        httpClient.close();
    }

    @Test
    public void testXbibOrgWithProxy() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .setHttpProxyHandler(new InetSocketAddress("80.241.223.251", 8080))
                .setConnectTimeoutMillis(30000)
                .setReadTimeoutMillis(30000)
                .build();
        httpClient.prepareGet()
                .setURL("http://xbib.org")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testXbibOrgWithVeryShortReadTimeout() throws Exception {
        logger.log(Level.FINE, "start");
        HttpClient httpClient = HttpClient.builder()
                .setReadTimeoutMillis(50)
                .build();
        httpClient.prepareGet()
                .setURL("http://xbib.org")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .execute()
                .get();
        httpClient.close();
        logger.log(Level.FINE, "end");
    }

    @Test
    public void testXbibTwoSequentialRequests() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();

        httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("http://xbib.org")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();

        httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("http://xbib.org")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();

        httpClient.close();
    }
}
