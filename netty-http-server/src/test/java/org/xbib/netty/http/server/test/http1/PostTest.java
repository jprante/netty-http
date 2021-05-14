package org.xbib.netty.http.server.test.http1;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

@ExtendWith(NettyHttpTestExtension.class)
class PostTest {

    private static final Logger logger = Logger.getLogger(PostTest.class.getName());

    @Test
    void testPostHttp1() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending OK");
                    if ("Hello World".equals(parameters.get("withspace"))) {
                        success2.set(true);
                    }
                    // ISO-8859
                    if ("JÃ¶rg".equals(parameters.get("name"))) {
                        success3.set(true);
                    }
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success1.set(true);
                }
            };
            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.ISO_8859_1)
                    .addParameter("a", "b")
                    .addFormParameter("my param", "my value")
                    .addFormParameter("withspace", "Hello World")
                    .addFormParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        logger.log(Level.INFO, "1=" + success1 + " 2=" + success2 + " 3=" + success3);
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

    @Test
    void testPostUnicodeHttp1() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending OK");
                    if ("Hello World".equals(parameters.get("withspace"))) {
                        success2.set(true);
                    }
                    if ("Jörg".equals(parameters.get("name"))) {
                        success3.set(true);
                    }
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success1.set(true);
                }
            };
            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.UTF_8)
                    .addParameter("a", "b")
                    .addFormParameter("my param", "my value")
                    .addFormParameter("withspace", "Hello World")
                    .addFormParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

    @Test
    void testFormPostHttp1() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        final AtomicBoolean success4 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending OK");
                    if ("Hello World".equals(parameters.get("withplus"))) {
                        success2.set(true);
                    }
                    if ("Jörg".equals(parameters.get("name"))) {
                        success3.set(true);
                    }
                    if ("my value".equals(parameters.get("my param"))) {
                        success4.set(true);
                    }
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    logger.log(Level.INFO, resp.getBodyAsString(StandardCharsets.UTF_8));
                    success1.set(true);
                }
            };
            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.UTF_8)
                    .addParameter("a", "b")
                    // test 'plus' encoding
                    .addFormParameter("my param", "my value")
                    .addFormParameter("withplus", "Hello World")
                    .addFormParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
        assertTrue(success4.get());
    }

    @Test
    void testTextPlainPostHttp1() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        final AtomicBoolean success4 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending OK");
                    if ("Hello World".equals(parameters.get("withoutplus"))) {
                        success2.set(true);
                    }
                    if ("Jörg".equals(parameters.get("name"))) {
                        success3.set(true);
                    }
                    if ("my value".equals(parameters.get("my param"))) {
                        success4.set(true);
                    }
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    logger.log(Level.INFO, resp.getBodyAsString(StandardCharsets.UTF_8));
                    success1.set(true);
                }
            };
            Request postRequest = Request.post()
                    .setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.TEXT_PLAIN, StandardCharsets.UTF_8)
                    // you can not pass form parameters on content type "text/plain"
                    .addParameter("a", "b")
                    .addParameter("my param", "my value")
                    .addParameter("withoutplus", "Hello World")
                    .addParameter("name", "Jörg")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
        assertTrue(success4.get());
    }

    @Test
    void testPostInvalidPercentEncodings() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        final AtomicBoolean success3 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/post", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request " + parameters.toString() + ", sending OK");
                    if ("myÿvalue".equals(parameters.get("my param"))) {
                        success1.set(true);
                    }
                    if ("bÿc".equals(parameters.get("a"))) {
                        success2.set(true);
                    }
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        try {
            server.accept();
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success3.set(true);
                }
            };
            Request postRequest = Request.post().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/post/test.txt"))
                    .contentType(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED, StandardCharsets.ISO_8859_1)
                    .addRawParameter("a", "b%YYc")
                    .addRawFormParameter("my param", "my%ZZvalue")
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
        assertTrue(success2.get());
        assertTrue(success3.get());
    }

}
