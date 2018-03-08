package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger(Http2Test.class.getName());

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
     * @throws IOException
     */
    @Test
    @Ignore
    public void testAkamai() throws IOException {
        Client client = Client.builder()
                .enableDebug()
                .addServerNameForIdentification("http2.akamai.com")
                .build();
        try {
            Request request = Request.get()
                    .url("https://http2.akamai.com/demo/h2_demo_frame.html")
                    //.url("https://http2.akamai.com/")
                    .setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> {
                        String response = msg.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + msg.status() +
                                msg.headers().entries() + " " + response);
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testWebtide() throws Exception {
        Client client = Client.builder().enableDebug().build();
        client.logDiagnostics(Level.INFO);
        try {
            Request request = Request.get().url("https://webtide.com").setVersion("HTTP/2.0").build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                    msg.headers().entries() +
                    //msg.content().toString(StandardCharsets.UTF_8) +
                    " status=" + msg.status().code()));
            client.execute(request).get();
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testHttp2PushIO() throws IOException {
        String url = "https://http2-push.io";
        Client client = Client.builder()
                .enableDebug()
                .addServerNameForIdentification("http2-push.io")
                .build();
        try {
            Request request = Request.builder(HttpMethod.GET)
                    .url(url).setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));
            client.execute(request).get();

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testWebtideTwoRequestsOnSameConnection() throws IOException {
        Client client = new Client();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .url("https://webtide.com").setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));

            Request request2 = Request.builder(HttpMethod.GET)
                    .url("https://webtide.com/why-choose-jetty/").setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));

            client.execute(request1).execute(request2);
        } finally {
            client.shutdownGracefully();
        }
    }
}
