package org.xbib.netty.http.server.test.http2;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpTestExtension.class)
class PostTest {

    private static final Logger logger = Logger.getLogger(PostTest.class.getName());

    @Test
    void testPostHttp2() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + " , sending, OK");
                    if ("Hello World".equals(parameters.getFirst("withspace"))) {
                        success2.set(true);
                    }
                    if ("Jörg".equals(parameters.getFirst("name"))) {
                        success3.set(true);
                    }
                    ServerResponse.write(resp, HttpResponseStatus.OK);
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success1.set(true);
                }
            };
            Request postRequest = Request.post().setVersion("HTP/2.0")
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.ISO_8859_1)
                    .addParameter("a", "b")
                    .addFormParameter("withspace", "Hello World")
                    .addFormParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }


    @Test
    void testPostUnicodeHttp2() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + " , sending, OK");
                    if ("Hello World".equals(parameters.getFirst("withspace"))) {
                        success2.set(true);
                    }
                    if ("Jörg".equals(parameters.getFirst("name"))) {
                        success3.set(true);
                    }
                    ServerResponse.write(resp, HttpResponseStatus.OK);
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success1.set(true);
                }
            };
            Request postRequest = Request.post().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.UTF_8)
                    .addParameter("a", "b")
                    .addFormParameter("withspace", "Hello World")
                    .addFormParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }
}
