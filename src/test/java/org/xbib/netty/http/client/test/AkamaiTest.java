package org.xbib.netty.http.client.test;

import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.test.LoggingBase;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@Ignore
public class AkamaiTest extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    /**
     * h2_demo_frame.html fails with:
     * 2018-02-27 23:43:32.048 INFORMATION [client] io.netty.handler.codec.http2.Http2FrameLogger
     * logRstStream [id: 0x4fe29f1e, L:/192.168.178.23:49429 - R:http2.akamai.com/104.94.191.203:443]
     * INBOUND RST_STREAM: streamId=2 errorCode=8
     * 2018-02-27 23:43:32.049 SCHWERWIEGEND [] org.xbib.netty.http.client.test.a.AkamaiTest lambda$testAkamaiHttps$0
     * HTTP/2 to HTTP layer caught stream reset
     * io.netty.handler.codec.http2.Http2Exception$StreamException: HTTP/2 to HTTP layer caught stream reset
     *
     * TODO(jprante) catch all promised pushes
     */
    @Test
    public void testAkamaiHttps() {
        Client client = new Client();
        try {
            Request request = Request.get()
                    //.setURL("https://http2.akamai.com/demo/h2_demo_frame.html")
                    .setURL("https://http2.akamai.com/")
                    .setVersion("HTTP/2.0")
                    .build()
                    .setExceptionListener(e -> logger.log(Level.SEVERE, e.getMessage(), e))
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status()
                                + " response body = " + response);
                    })
                    .setPushListener((requestHeaders, fullHttpResponse) -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "received push: request headers = " + requestHeaders
                                + " status = " + fullHttpResponse.status()
                                + " response headers = " + fullHttpResponse.headers().entries()
                                + " response body = " + response
                        );
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }
}
