package org.xbib.netty.http.client.test;

import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.HttpClient;
import org.xbib.netty.http.client.HttpRequestBuilder;
import org.xbib.netty.http.client.HttpRequestContext;

import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class GoogleTest {

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
    public void testGoogleHttp1() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setURL("http://www.google.com")
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
    public void testGoogleWithoutFollowRedirects() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setURL("http://google.com")
                .setFollowRedirect(false)
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        logger.log(Level.INFO, "pool size = " + httpClient.poolMap().size());
        httpClient.close();
    }

    @Test
    public void testGoogleHttps1() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setURL("https://www.google.com")
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
    public void testGoogleHttp2() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();

        httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://www.google.com")
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
    public void testGoogleHttpTwo() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();

        HttpRequestBuilder builder1 = httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://www.google.com")
                .setTimeout(10000)
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                });

        HttpRequestBuilder builder2 = httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://www.google.com")
                .setTimeout(10000)
                .onError(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                });

        // only sequential ... this sucks.

        HttpRequestContext context1 = builder1.execute();
        context1.get();

        HttpRequestContext context2 = builder2.execute();
        context2.get();

        httpClient.close();
    }
}
