package org.xbib.netty.http.server.test.endpoint;

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
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpTestExtension.class)
class ClassloaderServiceTest {

    private static final Logger logger = Logger.getLogger(ClassloaderServiceTest.class.getName());

    @Test
    void testClassloaderFileResource() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/classloader", "/**",
                        new ClassLoaderService(ClassloaderServiceTest.class, "/cl"))
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/classloader/test.txt"))
                    .build()
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg", resp.getBodyAsString(StandardCharsets.UTF_8));
                            count.incrementAndGet();
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getMessage());
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

    @Test
    void testClassloaderDirectoryResource() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/classloader", "/**",
                        new ClassLoaderService(ClassloaderServiceTest.class, "/cl"))
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/classloader"))
                    .build()
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.NOT_FOUND.code()) {
                            count.incrementAndGet();
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getMessage());
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
