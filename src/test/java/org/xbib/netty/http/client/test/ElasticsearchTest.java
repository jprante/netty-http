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

import org.junit.Test;
import org.xbib.netty.http.client.HttpClient;
import org.xbib.netty.http.client.HttpRequestBuilder;
import org.xbib.netty.http.client.HttpRequestContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class ElasticsearchTest {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %2$s %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(Level.INFO);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(Level.INFO);
        }
    }

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testElasticsearchCreateDocument() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        HttpRequestContext requestContext = httpClient.preparePut()
                .setURL("http://localhost:9200/test/test/1")
                .json("{\"text\":\"Hello World\"}")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .execute()
                .get();
        httpClient.close();
        logger.log(Level.FINE, "took = " + requestContext.took());
    }

    @Test
    public void testElasticsearchMatchQuery() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        HttpRequestContext requestContext = httpClient.preparePost()
                .setURL("http://localhost:9200/test/_search")
                .json("{\"query\":{\"match\":{\"_all\":\"Hello World\"}}}")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .execute()
                .get();
        httpClient.close();
        logger.log(Level.FINE, "took = " + requestContext.took());
    }

    @Test
    public void testElasticsearchConcurrent() throws Exception {
        int max = 100;
        HttpClient httpClient = HttpClient.builder()
                .build();
        List<HttpRequestBuilder> queries = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            queries.add(createQuery(httpClient));
        }
        List<HttpRequestContext> contexts = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            contexts.add(queries.get(i).execute());
        }
        List<HttpRequestContext> responses = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            responses.add(contexts.get(i).get());
        }
        for (int i = 0; i < max; i++) {
            logger.log(Level.FINE, "took = " + responses.get(i).took());
        }
        httpClient.close();
        logger.log(Level.INFO, "pool peak = " + httpClient.poolMap().getHttpClientChannelPoolHandler().getPeak());
    }

    private HttpRequestBuilder createQuery(HttpClient httpClient) throws IOException {
        return httpClient.preparePost()
                .setURL("http://localhost:9200/test/_search")
                .json("{\"query\":{\"match\":{\"_all\":\"Hello World\"}}}")
                .addHeader("connection", "keep-alive")
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e));
    }
}
