package org.xbib.netty.http.server.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.server.Server;

@Disabled
class ServerTest {

    @Test
    void testServer() throws Exception {
        Server server = Server.builder()
                .build();
        server.getDefaultVirtualServer().addHandler("/", (request, response) ->
                response.write("Hello World"));
        try {
            server.accept().channel().closeFuture().sync();
        } finally {
            server.shutdownGracefully();
        }
    }
}
