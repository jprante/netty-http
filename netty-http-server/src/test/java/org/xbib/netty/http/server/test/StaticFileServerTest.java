package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.context.NioContextHandler;
import org.xbib.netty.http.server.context.VirtualServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StaticFileServerTest {

    private static final Logger logger = Logger.getLogger(StaticFileServerTest.class.getName());

    @Test
    public void testStaticFileServer() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        Server server = Server.builder()
                .addVirtualServer(new VirtualServer().addContext("/static", new NioContextHandler(vartmp)))
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
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            logger.log(Level.INFO, request.toString());
            client.execute(request).get();
            logger.log(Level.INFO, "request complete");
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
        }
        assertTrue(success.get());
    }
}
