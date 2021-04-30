package org.xbib.netty.http.server.test.http1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.MixedFileUpload;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.HttpServerDomain;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpTestExtension.class)
class MimeUploadTest {

    private static final Logger logger = Logger.getLogger(MimeUploadTest.class.getName());

    @Test
    void testMimetHttp1() throws Exception {
        final AtomicBoolean success1 = new AtomicBoolean(false);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        HttpServerDomain domain = HttpServerDomain.builder(httpAddress)
                .singleEndpoint("/upload", "/**", (req, resp) -> {
                    HttpParameters parameters = req.getParameters();
                    logger.log(Level.INFO, "got request, headers = " + req.getHeaders() +
                            " params = " + parameters.toString() +
                            " body = " + req.getContent().toString(StandardCharsets.UTF_8));
                    resp.getBuilder().setStatus(HttpResponseStatus.OK.code()).build().flush();
                },  "POST")
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .enableDebug()
                .build();
        try {
            server.accept();

            ByteBuf byteBuf = Unpooled.buffer();
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            int max = 10 * 1024;
            for (int i = 0; i < max; i++) {
                outputStream.writeBytes("Hi");
            }
            MixedFileUpload upload = new MixedFileUpload("Test upload",
                    "test.txt", "text/plain", "binary",
                    StandardCharsets.UTF_8, byteBuf.readableBytes(), 10 * 1024);
            upload.setContent(byteBuf);
            ResponseListener<HttpResponse> responseListener = (resp) -> {
                if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                    success1.set(true);
                }
            };
            Request postRequest = Request.post()
                    .setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/upload"))
                    .addBodyData(upload)
                    .setResponseListener(responseListener)
                    .build();
            client.execute(postRequest).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success1.get());
    }

}
