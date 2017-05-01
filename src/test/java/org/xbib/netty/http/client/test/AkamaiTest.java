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
    public void testAkamai() throws Exception {

        // here we can not deal with server PUSH_PROMISE as response to headers, a go-away frame is written.
        // Probably because promised stream id is 2 which is smaller than 3?

        /*
        ----------------INBOUND--------------------
        [id: 0x27d23874, L:/192.168.178.23:52376 - R:http2.akamai.com/104.94.191.203:443]
        PUSH_PROMISE: streamId=3, promisedStreamId=2, headers=DefaultHttp2Headers[:method: GET,
        :path: /resources/push.css, :authority: http2.akamai.com, :scheme: https, host: http2.akamai.com,
        user-agent: XbibHttpClient/unknown (Java/Azul Systems, Inc./1.8.0_112) (Netty/4.1.9.Final),
        accept-encoding: gzip], padding=0
                ------------------------------------
        2017-05-01 18:53:46.076 AM FEINSTEN [org.xbib.netty.http.client.HttpClientChannelInitializer]
        io.netty.handler.codec.http2.Http2FrameLogger log
        ----------------OUTBOUND--------------------
        [id: 0x27d23874, L:/192.168.178.23:52376 - R:http2.akamai.com/104.94.191.203:443]
        GO_AWAY: lastStreamId=2, errorCode=1, length=75, bytes=556e7265636f676e697a656420485454...
         */

        HttpClient httpClient = HttpClient.builder()
                .build();

        httpClient.prepareGet()
                .setVersion("HTTP/2.0")
                .setURL("https://http2.akamai.com/demo/h2_demo_frame.html")
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
