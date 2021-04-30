package org.xbib.netty.http.server.test.http2;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;

@ExtendWith(NettyHttpTestExtension.class)
class PutTest {

    private static final Logger logger = Logger.getLogger(PutTest.class.getName());

    @Test
    void testPutHttp2() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/put", "/**", (req, resp) -> {
                    logger.log(Level.INFO, "got request " +
                            req.getContent().toString(StandardCharsets.UTF_8));
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                    success1.set(true);
                }, "PUT")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp.getStatus());
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success2.set(true);
                }
            };
            Request postRequest = Request.put()
                    .setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/put/test.txt"))
                    .addParameter("a", "b")
                    .content("Hello Jörg", "text/plain")
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
    }

    @Test
    void testLargePutHttp2() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        ByteBuf buffer = Unpooled.buffer();
        ByteBufOutputStream outputStream = new ByteBufOutputStream(buffer);
        int max = 64 * 1024 * 1024;
        for (int i = 0; i < max; i++) {
            outputStream.write(1);
        }
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/put", "/**", (req, resp) -> {
                    logger.log(Level.INFO, "got request, length = " +
                            req.getContent().readableBytes());
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                    success1.set(true);
                }, "PUT")
                .build();
        Server server = Server.builder(domain)
                .setMaxContentLength(max)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp.getStatus());
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success2.set(true);
                }
            };
            Request postRequest = Request.put()
                    .setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/put/test.txt"))
                    .content(buffer)
                    .contentType("application/octet-stream")
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
    }
}
