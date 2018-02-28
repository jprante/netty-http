package org.xbib.netty.http.client.rest;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.transport.Transport;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class RestClient {

    private static final Logger logger = Logger.getLogger(RestClient.class.getName());

    private Client client;

    private Transport transport;

    private FullHttpResponse response;

    private RestClient(Client client, Transport transport) {
        this.client = client;
        this.transport = transport;
    }

    public void setResponse(FullHttpResponse response) {
        this.response = response.copy();
    }

    public String asString() {
        ByteBuf byteBuf = response != null ? response.content() : null;
        return byteBuf != null && byteBuf.isReadable() ? response.content().toString(StandardCharsets.UTF_8) : null;
    }

    public static RestClient get(String urlString) throws IOException {
        URL url = URL.create(urlString);
        Client client = new Client();
        Transport transport = client.newTransport(HttpAddress.http1(url));
        RestClient restClient = new RestClient(client, transport);
        transport.setResponseListener(restClient::setResponse);
        try {
            transport.connect();
        } catch (InterruptedException e) {
            throw new ConnectException("unable to connect to " + url);
        }
        transport.awaitSettings();
        transport.execute(Request.builder(HttpMethod.GET).setURL(url).build());
        transport.get();
        return restClient;
    }
}
