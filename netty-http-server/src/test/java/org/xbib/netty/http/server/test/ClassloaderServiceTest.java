package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.endpoint.service.ClassLoaderService;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpExtension.class)
class ClassloaderServiceTest {

    private static final Logger logger = Logger.getLogger(ClassloaderServiceTest.class.getName());

    @Test
    void testSimpleClassloader() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/classloader", "/**",
                        new ClassLoaderService(ClassloaderServiceTest.class, "/cl"))
                .build();
        Server server = Server.builder(domain)
                .enableDebug()
                .build();
        server.logDiagnostics(Level.INFO);
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/classloader/test.txt"))
                    .build()
                    .setResponseListener(r -> {
                        if (r.status().equals(HttpResponseStatus.OK)) {
                            assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                            count.incrementAndGet();
                        }
                    });
            for (int i = 0; i < max; i++) {
                client.execute(request).get();
            }
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertEquals(max, count.get());
    }
}
