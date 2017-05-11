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
public class WebtideTest {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$-7s [%3$s] %2$s %5$s %6$s%n");
        LogManager.getLogManager().reset();
        Logger rootLogger = LogManager.getLogManager().getLogger("");
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(handler);
        rootLogger.setLevel(Level.FINE);
        for (Handler h : rootLogger.getHandlers()) {
            handler.setFormatter(new SimpleFormatter());
            h.setLevel(Level.FINE);
        }
    }

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testWebtide() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();

        httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://webtide.com")
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onResponse(fullHttpResponse -> {
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status()
                            + " response headers = " + fullHttpResponse.headers().entries()
                            );
                })
                .onPushReceived((headers, fullHttpResponse) -> {
                    logger.log(Level.INFO, "received push promise: request headers = " + headers
                            + " status = " + fullHttpResponse.status()
                            + " response headers = " + fullHttpResponse.headers().entries()
                            );
                })
                .execute()
                .get();

        httpClient.close();
    }
}
