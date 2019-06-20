package org.xbib.netty.http.server.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.NamedServer;

@Disabled
class ServerTest {

    @Test
    void testServer() throws Exception {
        NamedServer namedServer = NamedServer.builder(HttpAddress.http1("localhost", 8008), "*")
                .singleEndpoint("/", (request, response) -> ServerResponse.write(response, "Hello World"))
                .build();
        Server server = Server.builder(namedServer).build();
        try {
            server.accept().channel().closeFuture().sync();
        } finally {
            server.shutdownGracefully();
        }
    }
}
