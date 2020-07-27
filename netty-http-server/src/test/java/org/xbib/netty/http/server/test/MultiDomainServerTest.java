package org.xbib.netty.http.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.HttpServerDomain;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@Disabled
@ExtendWith(NettyHttpTestExtension.class)
class MultiDomainServerTest {

    private static final Logger logger = Logger.getLogger(MultiDomainServerTest.class.getName());

    @Test
    void testServer() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain fl = HttpServerDomain.builder(httpAddress, "fl.hbz-nrw.de")
                .singleEndpoint("/", (request, response) ->
                        response.write( "Hello fl.hbz-nrw.de"))
                .build();
        HttpServerDomain zfl2 = HttpServerDomain.builder(fl)
                .setServerName("zfl2.hbz-nrw.de")
                .singleEndpoint("/", (request, response) ->
                        response.write("Hello zfl2.hbz-nrw.de"))
                .build();
        Server server = Server.builder(fl)
                .addDomain(zfl2)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            Request request = Request.get()
                    .url("http://fl.hbz-nrw.de:8008")
                    .setResponseListener(resp -> {
                        String response = resp.getBodyAsString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "fl: got response: " + response + " status=" + resp.getStatus());
                        assertEquals("Hello fl.hbz-nrw.de", response);
                    })
                    .build();
            client.execute(request).get();
            request = Request.get()
                    .url("http://zfl2.hbz-nrw.de:8008")
                    .setResponseListener(resp -> {
                        String response = resp.getBodyAsString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "zfl2: got response: " + response + " status=" + resp.getStatus());
                        assertEquals("Hello zfl2.hbz-nrw.de", response);
                    })
                    .build();
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
    }
}
