package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.Ignore;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.transport.Transport;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientTest {

    private static final Logger logger = Logger.getLogger(ClientTest.class.getName());

    @Test
    @Ignore
    public void testHttp1() throws Exception {
        Client client = new Client();
        try {
            Transport transport = client.newTransport(HttpAddress.http1("fl.hbz-nrw.de"));
            transport.setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                    msg.headers().entries() +
                    msg.content().toString(StandardCharsets.UTF_8) +
                    " status=" + msg.status().code()));
            transport.connect();
            transport.awaitSettings();
            simpleRequest(transport);
            transport.get();
            transport.close();
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Ignore
    public void testHttp1ParallelRequests() {
        Client client = new Client();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .setURL("http://fl.hbz-nrw.de").setVersion("HTTP/1.1")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));
            Request request2 = Request.builder(HttpMethod.GET)
                    .setURL("http://fl.hbz-nrw.de/app/fl/").setVersion("HTTP/1.1")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));

            client.execute(request1);
            client.execute(request2);

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    @Ignore
    public void testHttp2() throws Exception {
        String host = "webtide.com";
        Client client = new Client();
        client.logDiagnostics(Level.INFO);
        try {
            Transport transport = client.newTransport(HttpAddress.http2(host));
            transport.setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                    msg.headers().entries() +
                    msg.content().toString(StandardCharsets.UTF_8) +
                    " status=" + msg.status().code()));
            transport.setPushListener((hdrs, msg) -> logger.log(Level.INFO, "got push: " +
                    msg.headers().entries() +
                    msg.content().toString(StandardCharsets.UTF_8)));
            transport.connect();
            transport.awaitSettings();
            simpleRequest(transport);
            transport.get();
            transport.close();
        } finally {
            client.shutdown();
        }
    }

    @Test
    public void testHttp2Request() {
        //String url = "https://webtide.com";
        String url = "https://http2-push.io";
        // TODO register push announces into promises in order to wait for them all.
        Client client = new Client();
        try {
            Request request = Request.builder(HttpMethod.GET)
                    .setURL(url).setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()))
                    .setPushListener((hdrs, msg) -> logger.log(Level.INFO, "got push: " +
                                    msg.headers().entries() +
                            msg.content().toString(StandardCharsets.UTF_8))
                    );
            client.execute(request).get();

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    @Ignore
    public void testHttp2TwoRequestsOnSameConnection() {
        Client client = new Client();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .setURL("https://webtide.com").setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()))
                    .setPushListener((hdrs, msg) -> logger.log(Level.INFO, "got push: " +
                            msg.headers().entries()
                            //msg.content().toString(StandardCharsets.UTF_8))
                            ));

            Request request2 = Request.builder(HttpMethod.GET)
                    .setURL("https://webtide.com/why-choose-jetty/").setVersion("HTTP/2.0")
                    .build()
                    .setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()))
                    .setPushListener((hdrs, msg) -> logger.log(Level.INFO, "got push: " +
                            msg.headers().entries() +
                            //msg.content().toString(StandardCharsets.UTF_8) +
                            " status=" + msg.status().code()));

            client.execute(request1).execute(request2);

        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    @Ignore
    public void testMixed() throws Exception {
        Client client = new Client();
        try {
            Transport transport = client.newTransport(HttpAddress.http1("xbib.org"));
            transport.setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                    msg.content().toString(StandardCharsets.UTF_8)));
            transport.connect();
            transport.awaitSettings();
            simpleRequest(transport);
            transport.get();
            transport.close();

            transport = client.newTransport(HttpAddress.http2("google.com"));
            transport.setResponseListener(msg -> logger.log(Level.INFO, "got response: " +
                    msg.content().toString(StandardCharsets.UTF_8)));
            transport.connect();
            transport.awaitSettings();
            simpleRequest(transport);
            transport.get();
            transport.close();
        } finally {
            client.shutdown();
        }
    }

    private void simpleRequest(Transport transport) {
        transport.execute(Request.builder(HttpMethod.GET).setURL(transport.httpAddress().base()).build());
    }

}
