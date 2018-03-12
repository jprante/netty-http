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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MultithreadedCleartextHttp2Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    /**
     * 2018-03-09 18:27:08.975 WARNUNG [io.netty.channel.ChannelInitializer]
     * io.netty.channel.ChannelInitializer exceptionCaught Failed to initialize a channel.
     * Closing: [id: 0x4af3e71a, L:/127.0.0.1:8008 - R:/127.0.0.1:59996]
     * io.netty.channel.ChannelPipelineException: org.xbib.netty.http.server.handler.Http2ServerConnectionHandler
     * is not a @Sharable handler, so can't be added or removed multiple times.
     * @throws Exception if test fails
     */
    @Test
    public void testmultithreadedCleartextHttp2() throws Exception {
        int loop = 1000;
        int threads = 4;
        // we assume slow server and reserve a large write buffer for the client, 32-64 bytes for each request,
        // to avoid channel.isWritable() drop-outs
        int low = 32 * loop;
        int high = 64 * loop;
        HttpAddress httpAddress = HttpAddress.of("localhost", 8008, HttpVersion.valueOf("HTTP/2.0"), false);
        Server server = Server.builder()
                .bind(httpAddress)
                .build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write("Hello World " + request.getRequest().content().toString(StandardCharsets.UTF_8)));
        server.accept();
        Client httpClient = Client.builder()
                .setWriteBufferWaterMark(new WriteBufferWaterMark(low, high))
                .build();
        AtomicInteger counter = new AtomicInteger();
        try {
            URL serverURL = server.getServerConfig().getAddress().base();
            HttpVersion serverVersion = server.getServerConfig().getAddress().getVersion();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                executorService.submit(() -> {
                    try {
                        Transport transport = httpClient.newTransport(serverURL, serverVersion);
                        for (int i = 0; i < loop; i++) {
                            Request request = Request.get().setVersion("HTTP/2.0")
                                    .content(Integer.toString(i), "text/plain")
                                    .build()
                                    .setResponseListener(fullHttpResponse -> {
                                        String content = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                                        //logger.log(Level.INFO, fullHttpResponse.toString() + " content=" + content);
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
                    } catch (IOException e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);
        } finally {
            httpClient.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter = " + counter.get());
    }
}
