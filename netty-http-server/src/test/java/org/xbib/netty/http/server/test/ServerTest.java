package org.xbib.netty.http.server.test;

import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.server.Server;

@Ignore
public class ServerTest {

    @Test
    public void testServer() throws Exception {
        Server server = Server.builder()
                .build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write("Hello World"));
        try {
            server.accept().channel().closeFuture().sync();
        } finally {
            server.shutdownGracefully();
        }
    }
}
