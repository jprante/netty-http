package org.xbib.netty.http.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@Disabled
@ExtendWith(NettyHttpTestExtension.class)
class MultiDomainSecureServerTest {

    private static final Logger logger = Logger.getLogger(MultiDomainSecureServerTest.class.getName());

    @Test
    void testSecureServer() throws Exception {
        InputStream certInputStream = getClass().getResourceAsStream("/fl-20210906.crt");
        if (certInputStream == null) {
            return;
        }
        InputStream keyInputStream = getClass().getResourceAsStream("/fl-20210906.pkcs8");
        if (keyInputStream == null) {
            return;
        }
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8443);
        HttpServerDomain fl = HttpServerDomain.builder(httpAddress, "fl.hbz-nrw.de")
                .setKeyCertChain(certInputStream)
                .setKey(keyInputStream, null)
                .singleEndpoint("/", (request, response) ->
                        response.write("Hello fl.hbz-nrw.de"))
                .build();
        HttpServerDomain zfl2 = HttpServerDomain.builder(fl)
                .setServerName("zfl2.hbz-nrw.de")
                .singleEndpoint("/", (request, response) ->
                        response.write( "Hello zfl2.hbz-nrw.de"))
                .build();
        Server server = Server.builder(fl)
                .addDomain(zfl2)
                .setTransportLayerSecurityProtocols("TLSv1.3")
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            Request request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url("https://fl.hbz-nrw.de:8443")
                    .setResponseListener(resp -> {
                        String response = resp.getBodyAsString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "fl: got response: " + response + " status=" + resp.getStatus());
                        assertEquals("Hello fl.hbz-nrw.de", response);
                    })
                    .build();
            client.execute(request).get();
            request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url("https://zfl2.hbz-nrw.de:8443")
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
