package org.xbib.netty.http.client.test.akamai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
public class AkamaiTest {

    private static Logger logger = Logger.getLogger(AkamaiTest.class.getName());

    /**
     * Problems with akamai:
     *
     * 2018-03-07 16:02:52.385 FEIN    [client] io.netty.handler.codec.http2.Http2FrameLogger logRstStream
     * [id: 0x57cc65bb, L:/10.1.1.94:52834 - R:http2.akamai.com/104.94.191.203:443] INBOUND RST_STREAM: streamId=2 errorCode=8
     * 2018-03-07 16:02:52.385 FEIN    [client] io.netty.handler.codec.http2.Http2FrameLogger logGoAway
     * [id: 0x57cc65bb, L:/10.1.1.94:52834 - R:http2.akamai.com/104.94.191.203:443] OUTBOUND GO_AWAY: lastStreamId=2 errorCode=0 length=0 bytes=
     *
     * demo/h2_demo_frame.html sends no content, only a push promise, and does not continue
     *
     * @throws IOException if test fails
     */
    @Test
    void testAkamai() throws IOException {
        Client client = Client.builder()
                .addServerNameForIdentification("http2.akamai.com")
                .build();
        try {
            Request request = Request.get()
                    .url("https://http2.akamai.com/demo/h2_demo_frame.html")
                    //.url("https://http2.akamai.com/")
                    .setVersion("HTTP/2.0")
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "status = " + resp.getStatus().getCode() +
                                resp.getHeaders() + " " + resp.getBodyAsString(StandardCharsets.UTF_8));
                    })
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }
}
