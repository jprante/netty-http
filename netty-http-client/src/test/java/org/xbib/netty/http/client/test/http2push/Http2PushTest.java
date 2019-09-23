package org.xbib.netty.http.client.test.http2push;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.test.NettyHttpTestExtension;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Disabled // /http2-push.io "connection refused"
@ExtendWith(NettyHttpTestExtension.class)
class Http2PushTest {

    private static final Logger logger = Logger.getLogger(Http2PushTest.class.getName());

    @Test
    void testHttp2PushIO() throws IOException {
        String url = "https://http2-push.io";
        Client client = Client.builder()
                .addServerNameForIdentification("http2-push.io")
                .build();
        try {
            Request request = Request.builder(HttpMethod.GET)
                    .url(url).setVersion("HTTP/2.0")
                    .setResponseListener(resp -> logger.log(Level.INFO,
                            "got response: " + resp.getHeaders() + " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get();

        } finally {
            client.shutdownGracefully();
        }
    }
}
