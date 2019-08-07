package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.Domain;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpExtension.class)
class PostTest {

    private static final Logger logger = Logger.getLogger(PostTest.class.getName());

    @Test
    void testPostHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got post " + parameters.toString());
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
            Request request = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .addParameter("a", "b")
                    .addFormParameter("name", "Jörg")
                    .build()
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            success.set(true);
                        }
                    });
            client.execute(request).get();
            logger.log(Level.INFO, "request complete");
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
                    logger.log(Level.INFO, "got post " + parameters.toString());
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
            Request request = Request.post().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .addParameter("a", "b")
                    .addFormParameter("name", "Jörg")
                    .build()
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            success.set(true);
                        }
                    });
            client.execute(request).get();
            logger.log(Level.INFO, "request complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }
}
