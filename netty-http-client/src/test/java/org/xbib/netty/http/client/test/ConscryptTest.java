package org.xbib.netty.http.client.test;

import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class ConscryptTest {

    private static final Logger logger = Logger.getLogger(ConscryptTest.class.getName());

    @Test
    void testConscrypt() throws IOException {

        Provider provider = Conscrypt.newProviderBuilder()
                .provideTrustManager(true)
                .build();

        Client client = Client.builder()
                .setJdkSslProvider()
                .setSslContextProvider(provider)
                .setTlsProtocols(new String[]{"TLSv1.2"}) // disable TLSv1.3 for Conscrypt
                .build();
        logger.log(Level.INFO, client.getClientConfig().toString());
        try {
            Request request = Request.get()
                    .url("https://google.com")
                    .setVersion("HTTP/1.1")
                    .build()
                    .setResponseListener(resp -> {
                        logger.log(Level.INFO, "status = " + resp.getStatus()
                                + " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
                    });
            client.execute(request).get();
        } finally {
            client.shutdownGracefully();
        }
    }
}
