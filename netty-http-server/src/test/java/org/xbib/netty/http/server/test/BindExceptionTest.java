package org.xbib.netty.http.server.test;

import io.netty.channel.ChannelFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.HttpServerDomain;

import java.io.IOException;
import java.net.BindException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class BindExceptionTest {

    @Test
    void testDoubleServer() throws IOException {
        HttpServerDomain domain = HttpServerDomain.builder(HttpAddress.http1("localhost", 8008))
                .singleEndpoint("/", (request, response) -> response.write("Hello World"))
                .build();
        Server server1 = Server.builder(domain).build();
        Server server2 = Server.builder(domain).build();
        try {
            Assertions.assertThrows(BindException.class, () ->{
                ChannelFuture channelFuture1 = server1.accept();
                assertNotNull(channelFuture1);
                ChannelFuture channelFuture2 = server2.accept();
                // should crash with BindException
                fail();
            });
        } finally {
            server1.shutdownGracefully();
            server2.shutdownGracefully();
        }
    }
}
