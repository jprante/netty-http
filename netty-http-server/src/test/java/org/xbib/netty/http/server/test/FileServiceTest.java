package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.endpoint.service.FileService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpTestExtension.class)
class FileServiceTest {

    private static final Logger logger = Logger.getLogger(FileServiceTest.class.getName());

    @Test
    void testFileServiceHttp1() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/static", "/**", new FileService(vartmp))
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static/test.txt"))
                    .build()
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            logger.log(Level.INFO, request.toString());
            client.execute(request).get();
            logger.log(Level.INFO, "request complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }

    @Test
    void testFileServiceHttp2() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/static", "/**", new FileService(vartmp))
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get()
                    .setVersion(HttpVersion.valueOf("HTTP/2.0"))
                    .url(server.getServerConfig().getAddress().base().resolve("/static/test.txt"))
                    .build()
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            logger.log(Level.INFO, request.toString());
            client.execute(request).get();
            logger.log(Level.INFO, "request complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }
}
