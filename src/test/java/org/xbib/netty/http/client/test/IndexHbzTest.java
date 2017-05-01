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
import org.xbib.netty.http.client.HttpRequestBuilder;
import org.xbib.netty.http.client.HttpRequestContext;

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
public class IndexHbzTest {

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
    public void testIndexHbz() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("http://index.hbz-nrw.de")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testIndexHbzHttps() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("https://index.hbz-nrw.de")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testIndexHbzWithCompletableFuture() throws Exception {
        // fetches "test" as content from index.hbz-nrw.de and continues with sending another URL to google.com

        // tricky: google.com does not completely redirect because the first httpResponseStringFunction wins
        // and generates the desired string result

        HttpClient httpClient = HttpClient.builder()
                .build();

        final Function<FullHttpResponse, String> httpResponseStringFunction =
                response -> response.content().toString(StandardCharsets.UTF_8);

        final CompletableFuture<String> completableFuture = httpClient.prepareGet()
                .setURL("http://index.hbz-nrw.de")
                .execute(httpResponseStringFunction)
                .exceptionally(Throwable::getMessage)
                .thenCompose(content -> httpClient.prepareGet()
                        .setURL("http://google.com/?query=" + content)
                        .execute(httpResponseStringFunction));

        String result = completableFuture.join();

        logger.log(Level.INFO, "completablefuture result = " + result);

        httpClient.close();
    }

    @Test
    public void testIndexHbzH2() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://index.hbz-nrw.de")
                .setTimeout(5000)
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testIndexHbzH2C() throws Exception {

        // times out waiting for http2 settings frame

        HttpClient httpClient = HttpClient.builder()
                .setInstallHttp2Upgrade(true)
                .build();
        httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("http://index.hbz-nrw.de")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

    @Test
    public void testIndexHbzConcurrent() throws Exception {

        HttpClient httpClient = HttpClient.builder()
                .build();

        HttpRequestBuilder builder1 = httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("http://index.hbz-nrw.de")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                });

        HttpRequestBuilder builder2 = httpClient.prepareGet()
                .setVersion("HTTP/1.1")
                .setURL("http://index.hbz-nrw.de")
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                });

        HttpRequestContext context1 = builder1.execute();
        HttpRequestContext context2 = builder2.execute();
        context1.get();
        context2.get();

        httpClient.close();
    }
}
