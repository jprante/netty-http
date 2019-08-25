package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.Domain;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpTestExtension.class)
class PostTest {

    private static final Logger logger = Logger.getLogger(PostTest.class.getName());

    @Test
    void testPostHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + " , sending, OK");
                    ServerResponse.write(resp, HttpResponseStatus.OK);
                }, "GET", "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            server.accept();

            ResponseListener<HttpResponse> responseListener = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp);
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success.set(true);
                }
            };

            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .addParameter("a", "b")
                    .addFormParameter("name", "Jörg")
                    .build()
                    .setResponseListener(responseListener);
            client.execute(postRequest).get();

            logger.log(Level.INFO, "complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }

    @Test
    void testPostHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString(), ", sending OK");
                    ServerResponse.write(resp, HttpResponseStatus.OK);
                }, "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            server.accept();

            ResponseListener<HttpResponse> responseListener = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp);
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success.set(true);
                }
            };

            Request postRequest = Request.post().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .addParameter("a", "b")
                    .addFormParameter("name", "Jörg")
                    .build()
                    .setResponseListener(responseListener);
            client.execute(postRequest).get();

            logger.log(Level.INFO, "complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }
}
