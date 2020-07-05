package org.xbib.netty.http.server.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@Disabled
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
        Domain fl = Domain.builder(httpAddress, "fl.hbz-nrw.de")
                .setKeyCertChain(certInputStream)
                .setKey(keyInputStream, null)
                .singleEndpoint("/", (request, response) -> ServerResponse.write(response, "Hello fl.hbz-nrw.de"))
                .build();
        Domain zfl2 = Domain.builder(fl)
                .setServerName("zfl2.hbz-nrw.de")
                .singleEndpoint("/", (request, response) -> ServerResponse.write(response, "Hello zfl2.hbz-nrw.de"))
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
                        logger.log(Level.INFO, "got response: " + response + " status=" + resp.getStatus());
                        assertEquals("Hello fl.hbz-nrw.de", response);
                    })
                    .build();
            client.execute(request).get();
            request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url("https://zfl2.hbz-nrw.de:8443")
                    .setResponseListener(resp -> {
                        String response = resp.getBodyAsString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "got response: " + response + " status=" + resp.getStatus());
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
