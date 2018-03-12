package org.xbib.netty.http.server.test;

import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CleartextHttp2Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger(CleartextHttp2Test.class.getName());

    @Test
    public void testCleartextHttp2() throws Exception {
        int loop = 1;
        // we assume slow server and reserve a large write buffer for the client, 32-64 bytes for each request,
        // to avoid channel.isWritable() drop-outs
        int low = 32 * loop;
        int high = 64 * loop;
        HttpAddress httpAddress = HttpAddress.of("localhost", 8008, HttpVersion.valueOf("HTTP/2.0"), false);
        Server server = Server.builder()
                .enableDebug()
                .bind(httpAddress)
                .build();
        //server.logDiagnostics(Level.INFO);
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write("Hello World " + request.getRequest().content().toString(StandardCharsets.UTF_8)));
        server.accept();
        Client httpClient = Client.builder()
                .enableDebug()
                .setWriteBufferWaterMark(new WriteBufferWaterMark(low, high))
                .build();
        AtomicInteger counter = new AtomicInteger();
        try {
            URL serverURL = server.getServerConfig().getAddress().base();
            HttpVersion serverVersion = server.getServerConfig().getAddress().getVersion();
            // yes, HTTP/2 uses a single transport, and we can send many thousand requests per second asynchronously
            Transport transport = httpClient.newTransport(serverURL, serverVersion);
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion("HTTP/2.0")
                        .url(server.getServerConfig().getAddress().base())
                        .content(Integer.toString(i), "text/plain")
                        .build()
                        .setResponseListener(fullHttpResponse -> {
                            String content = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                            logger.log(Level.INFO, fullHttpResponse.toString() + " content=" + content);
                            counter.incrementAndGet();
                        });
                // submit request
                transport.execute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
            }
            // wait for transport to complete
            transport.get();
        } finally {
            httpClient.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter = " + counter.get());
    }
}
