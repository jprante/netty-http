package org.xbib.netty.http.client.test;

import org.junit.Test;
import org.xbib.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 */
public class AkamaiTest {

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
    public void testAkamaiHttps() throws Exception {
        HttpClient httpClient = HttpClient.getInstance();
        httpClient.prepareGet("https://http2.akamai.com/demo/h2_demo_frame.html")
                .setHttp2()
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status()
                            + " response body = " + response);
                })
                .onPushReceived((requestHeaders, fullHttpResponse) -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "received push promise: request headers = " + requestHeaders
                            + " status = " + fullHttpResponse.status()
                            + " response headers = " + fullHttpResponse.headers().entries()
                            + " response body = " + response
                    );
                })
                .execute()
                .get();
        httpClient.close();
    }
}
