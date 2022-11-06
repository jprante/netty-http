package org.xbib.netty.http.server.test.http1;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpTestExtension.class)
class FailureTest {

    private static final Logger logger = Logger.getLogger(FailureTest.class.getName());

    /**
     * This test checks if the server hangs after a failure.
     * @throws Exception exception
     */
    @Test
    void testFail() throws Exception {
        URL url = URL.from("http://localhost:8008/domain/");
        HttpAddress httpAddress = HttpAddress.http1(url);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/fail", "/**", (req, resp) -> {
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
        try {
            server.accept();

            // send bad request

            /*Socket socket = new Socket(InetAddress.getByName(url.getHost()), url.getPort());
            PrintWriter pw = new PrintWriter(socket.getOutputStream());
            pw.println("GET /::{} HTTP/1.1");
            pw.println("Host: " + url.getHost() + ":" + url.getPort());
            pw.println("");
            pw.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String t;
            while ((t = br.readLine()) != null) {
                logger.log(Level.INFO, t);
            }
            br.close();*/

            // send good request

            ResponseListener<HttpResponse> responseListener1 = (resp) -> {
                logger.log(Level.INFO, "got response = " + resp.getStatus());
                success1.set(true);
            };
            Request getRequest = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/fail"))
                    .addParameter("a", "b")
                    .setResponseListener(responseListener1)
                    .build();
            client.execute(getRequest).get();

        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
    }
}
