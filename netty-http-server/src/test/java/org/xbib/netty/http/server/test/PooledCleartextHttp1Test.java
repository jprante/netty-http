package org.xbib.netty.http.server.test;

import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.transport.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PooledCleartextHttp1Test extends LoggingBase {

    private static final Logger logger = Logger.getLogger("");

    @Test
    public void testClearTextHttp1() throws Exception {
        int loop = 10000;
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        Server server = Server.builder()
                //.enableDebug()
                .bind(httpAddress).build();
        server.getDefaultVirtualServer().addContext("/", (request, response) -> {
            response.write("Hello World " + request.getRequest().content().toString(StandardCharsets.UTF_8));
        });
        server.accept();
        org.xbib.netty.http.common.HttpAddress poolNode = org.xbib.netty.http.common.HttpAddress.http1("localhost", 8008);
        Client httpClient = Client.builder()
                //.enableDebug()
                .addPoolNode(poolNode)
                .setPoolNodeConnectionLimit(8)
                .build();
        AtomicInteger counter = new AtomicInteger();
        try {
            for (int i = 0; i < loop; i++) {
                Request request = Request.get().setVersion("HTTP/1.1")
                        .url(server.getServerConfig().getAddress().base())
                        .addParameter("test", Integer.toString(i))
                        .content(Integer.toString(i), "text/plain")
                        .build()
                        .setResponseListener(fullHttpResponse -> {
                            String response = fullHttpResponse.content().toString(StandardCharsets.UTF_8);
                            //logger.log(Level.INFO, "status = " + fullHttpResponse.status() + " response body = " + response);
                            counter.incrementAndGet();
                        });
                Transport transport = httpClient.pooledExecute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
                // each execution needs to be synchronized
                transport.get();
            }
        } finally {
            httpClient.shutdownGracefully();
            server.shutdownGracefully();
        }
        logger.log(Level.INFO, "counter=" + counter.get());
    }
}
