package org.xbib.netty.http.server.test;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SecureHttp1Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testSecureHttp1() throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        Server server = Server.builder().bind(HttpAddress.secureHttp1("localhost", 8143))
                .setSelfCert()
                .build();
        Client httpClient = Client.builder()
                .trustInsecure()
                .build();
        try {
            server.getDefaultVirtualServer().addContext("/", (request, response) ->
                    response.write("Hello World"));
            server.accept();
            httpClient.execute(Request.get().setVersion("HTTP/1.1")
                    .url(server.getServerConfig().getAddress().base())
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    })).get();
            httpClient.execute(Request.get().setVersion("HTTP/1.1")
                    .url(server.getServerConfig().getAddress().base())
                    .build()
                    .setResponseListener(fullHttpResponse -> {
                        String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                        logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                    })).get();
        } finally {
            httpClient.shutdownGracefully();
            server.shutdownGracefully();
        }
    }
}
