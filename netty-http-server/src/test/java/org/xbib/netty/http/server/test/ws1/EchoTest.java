package org.xbib.netty.http.server.test.ws1;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EchoTest {

    private static final Logger logger = Logger.getLogger(EchoTest.class.getName());

    @Test
    void testBasicAuth() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/**", (request, response) -> {
                    String authorization = request.getHeader("Authorization");
                    response.getBuilder().setStatus(HttpResponseStatus.OK.code())
                            .setContentType("text/plain").build().write(authorization);
                })
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) ->
                    assertEquals("Basic aGVsbG86d29ybGQ=", resp.getBodyAsString(StandardCharsets.UTF_8));
            Request postRequest = Request.get()
                    .setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .addBasicAuthorization("hello", "world")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
    }
}
