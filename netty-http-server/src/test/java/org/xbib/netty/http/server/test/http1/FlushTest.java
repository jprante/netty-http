package org.xbib.netty.http.server.test.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class FlushTest {

    private static final Logger logger = Logger.getLogger(FlushTest.class.getName());

    /**
     * This test checks the flush() operation of the server response.
     * Flush uses Unpooled.buffer(0), and requires a fresh instance each call.
     * If a static Unpooled.buffer(0) is used, a retry will fail -
     * and the second "302 Found" will never be sent because of a server hang.
     * @throws Exception exception
     */
    @Test
    void testFlushHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/flush", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending 302 Found");
                    resp.getBuilder().setStatus(HttpResponseStatus.FOUND.code())
                            .build().flush();
                })
                .build();
        Server server = Server.builder(domain)
                .enableDebug()
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        try {
            server.accept();

            // first request to trigger flush()

            ResponseListener<HttpResponse> responseListener1 = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp.getStatus());
                if (resp.getStatus().getCode() == HttpResponseStatus.FOUND.code()) {
                    success1.set(true);
                }
            };
            Request getRequest = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/flush"))
                    .addParameter("a", "b")
                    .setResponseListener(responseListener1)
                    .build();
            client.execute(getRequest).get();

            // second request to trigger flush()

            ResponseListener<HttpResponse> responseListener2 = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp.getStatus());
                if (resp.getStatus().getCode() == HttpResponseStatus.FOUND.code()) {
                    success2.set(true);
                }
            };
            getRequest = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/flush"))
                    .addParameter("a", "b")
                    .setResponseListener(responseListener2)
                    .build();
            client.execute(getRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
    }
}
