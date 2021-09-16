package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.client.api.ClientTransport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class TransportLayerSecurityServerTest {

    private static final Logger logger = Logger.getLogger(TransportLayerSecurityServerTest.class.getName());

    @Test
    void testTLSDefaultSettings() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                        .setSelfCert()
                        .singleEndpoint("/", (request, response) ->
                                response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                        .write(request.getContent().toString(StandardCharsets.UTF_8)))
                        .build())
                .build();
        Client client = Client.builder()
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            counter.incrementAndGet();
        };
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .content("Hello Jörg", "text/plain")
                    .setResponseListener(responseListener)
                    .build();
            ClientTransport transport = client.execute(request).get();
            logger.log(Level.INFO, "TLS protocol = " + transport.getSession().getProtocol());
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testOpenSSL12() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp1("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build())
                .setOpenSSLSslProvider()
                .setTransportLayerSecurityProtocols("TLSv1.2")
                .build();
        Client client = Client.builder()
                .setOpenSSLSslProvider()
                .setTransportLayerSecurityProtocols("TLSv1.2")
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            counter.incrementAndGet();
        };
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base())
                    .content("Hello Jörg", "text/plain")
                    .setResponseListener(responseListener)
                    .build();
            ClientTransport transport = client.execute(request).get();
            logger.log(Level.INFO, "TLS protocol = " + transport.getSession().getProtocol());
            assertEquals("TLSv1.2", transport.getSession().getProtocol());
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }

    @Test
    void testOpenSSL13() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        Server server = Server.builder(HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                                .write(request.getContent().toString(StandardCharsets.UTF_8)))
                .build())
                .setOpenSSLSslProvider()
                .setTransportLayerSecurityProtocols("TLSv1.3")
                .build();
        Client client = Client.builder()
                .setOpenSSLSslProvider()
                .setTransportLayerSecurityProtocols("TLSv1.3")
                .trustInsecure()
                .build();
        AtomicInteger counter = new AtomicInteger();
        final ResponseListener<HttpResponse> responseListener = resp -> {
            logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                    " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            counter.incrementAndGet();
        };
        try {
            server.accept();
            Request request = Request.get()
                    .setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content("Hello Jörg", "text/plain")
                    .setResponseListener(responseListener)
                    .build();
            ClientTransport transport = client.execute(request).get();
            logger.log(Level.INFO, "TLS protocol = " + transport.getSession().getProtocol());
            assertEquals("TLSv1.3", transport.getSession().getProtocol());
        } finally {
            client.shutdownGracefully();
            server.shutdownGracefully();
        }
        assertEquals(1, counter.get());
    }
}
