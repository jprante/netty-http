package org.xbib.netty.http.server.test.endpoint;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.HttpEndpointResolver;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.endpoint.service.FileService;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpTestExtension.class)
class EndpointTest {

    private static final Logger logger = Logger.getLogger(EndpointTest.class.getName());

    @Test
    void testPrefixPathParameter() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver = HttpEndpointResolver.builder()
                .setPrefix("/files")
                .addEndpoint(HttpEndpoint.builder().setPath("/{mypath}").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.INFO, "dispatching endpoint = " + req.getEndpoint() +
                            " req = " + req +
                            " req context path = " + req.getContextPath() +
                            " effective path = " + req.getEffectiveRequestPath() +
                            " path params = " + req.getPathParameters());
                    assertEquals("test.txt", req.getPathParameters().get("mypath"));
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/files/test.txt"))
                    .setResponseListener(resp -> {
                        success.set(true);
                    })
                    .build();
            client.execute(request).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
    }

    @Test
    void testEmptyPrefixEndpoint() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        FileService fileService = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() +
                            " req = " + req + " req context path = " + req.getContextPath());
                    fileService.handle(req, resp);
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver)
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
                    .url(server.getServerConfig().getAddress().base().resolve("/test.txt"))
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success.set(true);
                    })
                    .build();
            client.execute(request).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }

    @Test
    void testPlainPrefixEndpoint() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        FileService fileService = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/").setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() +
                            " req = " + req + " req context path = " + req.getContextPath());
                    fileService.handle(req, resp);
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver)
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
                    .url(server.getServerConfig().getAddress().base().resolve("/test.txt"))
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success.set(true);
                    })
                    .build();
            client.execute(request).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }

    @Test
    void testSimplePathEndpoints() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        FileService fileService = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static1").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static2").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static3").setPath("/**").build())
                .setDispatcher(( req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() +
                            " req = " + req + " req context path = " + req.getContextPath());
                    fileService.handle(req, resp);
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test1.txt"), "Hello Jörg 1".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test2.txt"), "Hello Jörg 2".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test3.txt"), "Hello Jörg 3".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static1/test1.txt"))
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg 1", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success1.set(true);
                    })
                    .build();
            client.execute(request).get();
            Request request1 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static2/test2.txt"))
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg 2",resp.getBodyAsString(StandardCharsets.UTF_8));
                        success2.set(true);
                    })
                    .build();
            client.execute(request1).get();
            Request request2 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static3/test3.txt"))
                    .setResponseListener(resp -> {
                        assertEquals("Hello Jörg 3", resp.getBodyAsString(StandardCharsets.UTF_8));
                        success3.set(true);
                    })
                    .build();
            client.execute(request2).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test1.txt"));
            Files.delete(vartmp.resolve("test2.txt"));
            Files.delete(vartmp.resolve("test3.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

    @Test
    void testQueryEndpoints() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        FileService fileService = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static1").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static2").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static3").setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() + " req = " + req);
                    fileService.handle(req, resp);
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test1.txt"), "Hello Jörg 1".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test2.txt"), "Hello Jörg 2".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test3.txt"), "Hello Jörg 3".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request1 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static1/test1.txt"))
                    .addParameter("a", "b")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 1", resp.getBodyAsString(StandardCharsets.UTF_8));
                            success1.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request1).get();
            Request request2 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static2/test2.txt"))
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 2", resp.getBodyAsString(StandardCharsets.UTF_8));
                            success2.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request2).get();
            Request request3 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static3/test3.txt"))
                    .content("{\"a\":\"b\"}","application/json")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 3",resp.getBodyAsString(StandardCharsets.UTF_8));
                            success3.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request3).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
            Files.delete(vartmp.resolve("test1.txt"));
            Files.delete(vartmp.resolve("test2.txt"));
            Files.delete(vartmp.resolve("test3.txt"));
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

    @Test
    void testMultiResolver() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        FileService fileService1 = new FileService(vartmp);
        FileService fileService2 = new FileService(vartmp);
        FileService fileService3 = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver httpEndpointResolver1 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static1").setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() + " context path = " + req.getContextPath());
                    fileService1.handle(req, resp);
                })
                .build();
        HttpEndpointResolver httpEndpointResolver2 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static2").setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() + " context path = " + req.getContextPath());
                    fileService2.handle(req, resp);
                })
                .build();
        HttpEndpointResolver httpEndpointResolver3 = HttpEndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static3").setPath("/**").build())
                .setDispatcher((req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint = " + req.getEndpoint() + " context path = " + req.getContextPath());
                    fileService3.handle(req, resp);
                })
                .build();
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(httpEndpointResolver1)
                .addEndpointResolver(httpEndpointResolver2)
                .addEndpointResolver(httpEndpointResolver3)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test1.txt"), "Hello Jörg 1".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test2.txt"), "Hello Jörg 2".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test3.txt"), "Hello Jörg 3".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request1 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static1/test1.txt"))
                    .addParameter("a", "b")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 1", resp.getBodyAsString(StandardCharsets.UTF_8));
                            success1.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request1).get();
            Request request2 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static2/test2.txt"))

                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 2", resp.getBodyAsString(StandardCharsets.UTF_8));
                            success2.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request2).get();
            Request request3 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static3/test3.txt"))
                    .content("{\"a\":\"b\"}","application/json")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello Jörg 3",resp.getBodyAsString(StandardCharsets.UTF_8));
                            success3.set(true);
                        } else {
                            logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                        }
                    })
                    .build();
            client.execute(request3).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
            Files.delete(vartmp.resolve("test1.txt"));
            Files.delete(vartmp.resolve("test2.txt"));
            Files.delete(vartmp.resolve("test3.txt"));
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

    @Test
    void testMassiveEndpoints() throws IOException {
        int max = 1024; // the default limit, must work
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver.Builder endpointResolverBuilder = HttpEndpointResolver.builder()
                .setPrefix("/static");
        for (int i = 0; i < max; i++) {
            endpointResolverBuilder.addEndpoint(HttpEndpoint.builder()
                    .setPath("/" + i + "/**")
                    .build());
        }
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .addEndpointResolver(endpointResolverBuilder
                        .setDispatcher((req, resp) -> resp.getBuilder()
                                .setStatus(HttpResponseStatus.OK.code()).build().flush())
                        .build())
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            for (int i = 0; i < max; i++) {
                Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                        .url(server.getServerConfig().getAddress().base().resolve("/static/" + i + "/test.txt"))
                        .setResponseListener(resp -> {
                            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                                count.incrementAndGet();
                            } else {
                                logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                            }
                        })
                        .build();
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
    void testMassiveEndpointResolvers() throws IOException {
        int max = 1024; // the default limit, must work
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpEndpointResolver.Builder endpointResolverBuilder = HttpEndpointResolver.builder()
                .setPrefix("/static");
        HttpServerDomain.Builder domainBuilder = HttpServerDomain.builder(httpAddress);
        for (int i = 0; i < max; i++) {
            domainBuilder.addEndpointResolver(endpointResolverBuilder.addEndpoint(HttpEndpoint.builder()
                    .setPath("/" + i + "/**").build())
                    .setDispatcher((req, resp) -> resp.getBuilder()
                            .setStatus(HttpResponseStatus.OK.code()).build().flush())
                    .build());
        }
        Server server = Server.builder(domainBuilder.build())
                .build();
        Client client = Client.builder()
                .build();
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            for (int i = 0; i < max; i++) {
                Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                        .url(server.getServerConfig().getAddress().base().resolve("/static/" + i + "/test.txt"))
                        .setResponseListener(resp -> {
                            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                                count.incrementAndGet();
                            } else {
                                logger.log(Level.WARNING, resp.getStatus().getReasonPhrase());
                            }
                        })
                        .build();
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
