package org.xbib.netty.http.client.test.http1;

import io.netty.handler.codec.http.HttpMethod;
import org.junit.jupiter.api.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

class Http1Test {

    private static final Logger logger = Logger.getLogger(Http1Test.class.getName());

    @Test
    void testHttp1() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            Request request = Request.get()
                    .url("https://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.FINE,
                            "got response: " + resp.getHeaders() +
                            resp.getBodyAsString(StandardCharsets.UTF_8) +
                            " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get().close();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testHttpGetRequest() throws Exception {
        Client client = Client.builder()
                .enableDebug()
                .build();
        try {
            Map<String, Object> parameters = Map.of(
                    "version", "1.1",
                    "operation", "searchRetrieve",
                    "recordSchema", "MARC21plus-1-xml",
                    "query", "iss = 00280836"
            );
            Request request = Request.post()
                    .url("https://services.dnb.de/sru/zdb")
                    .setParameters(parameters)
                    .setResponseListener(resp -> logger.log(Level.INFO,
                            "got response: " + resp.getHeaders() +
                                    resp.getBodyAsString(StandardCharsets.UTF_8) +
                                    " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get().close();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testHttpsGetRequest() throws Exception {
        Client client = Client.builder()
                .enableDebug()
                .setJdkSslProvider()
                .build();
        try {
            Request request = Request.post()
                    .url("http://hebis.rz.uni-frankfurt.de/HEBCGI/vuefl_recv_data.pl")
                    .setResponseListener(resp -> logger.log(Level.INFO,
                            "got response: " + resp.getHeaders() +
                                    resp.getBodyAsString(StandardCharsets.UTF_8) +
                                    " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get().close();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testHttpsGetRequestHebisSRU() throws Exception {
        Client client = Client.builder()
                .enableDebug()
                .build();
        try {
            Request request = Request.get()
                    .url("http://sru.hebis.de/sru/DB=2.1?version=1.1&operation=searchRetrieve&recordSchema=marc21&query=prs%20=%20Smith&startRecord=1&maximumRecords=10")
                    .setResponseListener(resp -> logger.log(Level.INFO,
                            "got response: " + resp.getHeaders() +
                                    resp.getBodyAsString(StandardCharsets.UTF_8) +
                                    " status=" + resp.getStatus()))
                    .build();
            client.execute(request).get().close();
        } finally {
            client.shutdownGracefully();
        }
    }


    @Test
    void testSequentialRequests() throws Exception {
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.get().url("https://xbib.org")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request1).get();
            Request request2 = Request.get().url("http://google.com").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getBodyAsString(StandardCharsets.UTF_8)))
                    .build();
            client.execute(request2).get().close();
        } finally {
            client.shutdownGracefully();
        }
    }

    @Test
    void testParallelRequests() throws IOException {
        Client client = Client.builder()
                .build();
        try {
            Request request1 = Request.builder(HttpMethod.GET)
                    .url("https://xbib.org").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getHeaders() + " status=" +resp.getStatus()))
                    .build();
            Request request2 = Request.builder(HttpMethod.GET)
                    .url("https://xbib.org").setVersion("HTTP/1.1")
                    .setResponseListener(resp -> logger.log(Level.FINE, "got response: " +
                            resp.getHeaders() + " status=" +resp.getStatus()))
                    .build();
            for (int i = 0; i < 10; i++) {
                client.execute(request1);
                client.execute(request2);
            }

        } finally {
            client.shutdownGracefully();
        }
    }
}
