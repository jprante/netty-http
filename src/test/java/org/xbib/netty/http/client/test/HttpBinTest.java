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
public class HttpBinTest {

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

    /**
     * The reponse body should be
     * <pre>
     *   {
     *     "cookies": {
     *       "name": "value"
     *     }
     *   }
     * </pre>
     * @throws Exception
     */
    @Test
    public void testHttpBin() throws Exception {
        HttpClient httpClient = HttpClient.builder()
                .build();
        httpClient.prepareGet()
                .setURL("http://httpbin.org/cookies/set?name=value")
                .onException(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                .onCookie(cookie -> logger.log(Level.INFO, cookie.toString()))
                .onHeaders(headers -> logger.log(Level.INFO, headers.toString()))
                .onResponse(fullHttpResponse -> {
                    String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                    logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                })
                .execute()
                .get();
        httpClient.close();
    }

}
