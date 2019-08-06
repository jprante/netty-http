package org.xbib.netty.http.client.test.webtide;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.test.NettyHttpExtension;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpExtension.class)
class WebtideTest {

    private static final Logger logger = Logger.getLogger(WebtideTest.class.getName());

    @Test
    void testWebtide() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            Request request = Request.get().url("https://webtide.com").setVersion("HTTP/2.0").build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " + msg));
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testWebtideTwoRequestsOnSameConnection() throws IOException {
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