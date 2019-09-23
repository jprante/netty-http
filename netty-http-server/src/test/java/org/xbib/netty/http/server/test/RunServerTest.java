package org.xbib.netty.http.server.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.Domain;

@Disabled
class RunServerTest {

    @Test
    void testServer() throws Exception {
        Domain domain = Domain.builder(HttpAddress.http1("localhost", 8008), "*")
                .singleEndpoint("/", (request, response) -> ServerResponse.write(response, "Hello World"))
                .build();
        Server server = Server.builder(domain).build();
        try {
            server.accept().channel().closeFuture().sync();
        } finally {
            server.shutdownGracefully();
        }
    }
}
