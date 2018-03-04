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

public class Http2Test {

    private static final Logger logger = Logger.getLogger(Http2Test.class.getName());

    /**
     */
    @Test
    public void testAkamai() throws IOException {
        Client client = Client.builder().enableDebug().build();
        try {
            Request request = Request.get()
                    .url("https://http2.akamai.com/demo/h2_demo_frame.html")
                    //.url("https://http2.akamai.com/")
                    .setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status()
                                + " response body = " + response);
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
        //String url = "https://webtide.com";
        String url = "https://http2-push.io";
        // TODO register push announces into promises in order to wait for them all.
        Client client = Client.builder().enableDebug().build();
        try {
            Request request = Request.builder(HttpMethod.GET)
                    .url(url).setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));
            client.execute(request).get();

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    public void testWebtideTwoRequestsOnSameConnection() {
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
        } catch (IOException e) {
            //
        } finally {
            client.shutdownGracefully();
        }
    }
}
