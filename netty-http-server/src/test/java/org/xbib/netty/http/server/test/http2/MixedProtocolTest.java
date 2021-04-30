package org.xbib.netty.http.server.test.http2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ClientTransport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.util.concurrent.atomic.AtomicInteger;
import io.netty.handler.codec.http.HttpResponseStatus;

@ExtendWith(NettyHttpTestExtension.class)
class MixedProtocolTest {

    @Test
    void testHttp1ClientHttp2Server() throws Exception {
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/", (request, response) -> response.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush())
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion("HTTP/1.1")
                    .url(server.getServerConfig().getAddress().base().resolve("/"))
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED.code()) {
                            count.incrementAndGet();
                        }
                    })
                    .build();
            for (int i = 0; i < max; i++) {
                client.execute(request).get();
            }
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        assertEquals(max, count.get());
    }

    @Test
    void testHttp2ClientHttp1Server() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush())
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base().resolve("/"))
                    .setResponseListener(resp -> {
                        // do nothing
                    })
                    .build();
            for (int i = 0; i < max; i++) {
                // HTTP 2 breaks transport
                ClientTransport transport = client.execute(request).get();
                if (transport.isFailed()) {
                    count.incrementAndGet();
                }
            }
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        assertEquals(max, count.get());
    }

    @Disabled("negotiation does not work")
    @Test
    void testHttp1ClientHttp2ServerWithNegotiation() throws Exception {
        HttpAddress httpAddress = HttpAddress.secureHttp2("localhost", 8143);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .setSelfCert()
                .singleEndpoint("/", (request, response) ->
                        response.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush()
                )
                .build();
        Server server = Server.builder(domain)
                //.enableDebug()
                .setTransportLayerSecurityProtocols("TLSv1.2")
                .build();
        Client client = Client.builder()
                //.enableDebug()
                .trustInsecure()
                .enableNegotiation(true)
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            httpAddress = HttpAddress.secureHttp1("localhost", 8143);
            Request request = Request.get().setVersion("HTTP/1.1")
                    .url(httpAddress.base().resolve("/"))
                    .setResponseListener(resp -> {
                        count.incrementAndGet();
                    })
                    .build();
            for (int i = 0; i < max; i++) {
                ClientTransport transport = client.execute(request).get();
                if (transport.isFailed()) {
                    count.incrementAndGet();
                }
            }
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        assertEquals(max, count.get());
    }


}
