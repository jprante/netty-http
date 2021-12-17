package org.xbib.netty.http.server.test.http1;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpTestExtension.class)
class StreamTest {

    @Test
    void testServerBodyInputStreamHttp1() throws Exception {
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/", (request, response) -> {
                    ByteBufInputStream inputStream = request.getInputStream();
                    String content = inputStream.readLine();
                    assertEquals("my body parameter", content);
                    ByteBufOutputStream outputStream = response.newOutputStream();
                    outputStream.writeBytes("Hello World");
                    response.getBuilder().setStatus(HttpResponseStatus.OK.code()).setContentType("text/plain").build()
                            .write(outputStream);
                })
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        int max = 1;
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/"))
                    .content("my body parameter", "text/plain")
                    .setResponseListener(resp -> {
                        if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                            assertEquals("Hello World", resp.getBodyAsString(StandardCharsets.UTF_8));
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
}
