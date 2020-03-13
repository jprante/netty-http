package org.xbib.netty.http.server.test.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BasicAuthTest {

    private static final Logger logger = Logger.getLogger(PostTest.class.getName());

    @Disabled
    void testBasicAuth() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    logger.log(Level.INFO, "got response " + resp.getBodyAsString(StandardCharsets.UTF_8));
                }
            };
            URL serverUrl = URL.from("");
            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(serverUrl)
                    .addBasicAuthorization("", "")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
    }
}
