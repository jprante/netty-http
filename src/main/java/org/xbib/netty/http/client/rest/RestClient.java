package org.xbib.netty.http.client.rest;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.client.RequestBuilder;
import org.xbib.netty.http.client.transport.Transport;

import java.io.IOException;
import java.nio.charset.Charset;
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
        return method(urlString, null, null, HttpMethod.GET);
    }

    public static RestClient post(String urlString, String body) throws IOException {
        return method(urlString, body, null, HttpMethod.POST);
    }

    public static RestClient method(String urlString,
                                    String body, Charset charset,
                                    HttpMethod httpMethod) throws IOException {
        URL url = URL.create(urlString);
        Client client = new Client();
        Transport transport = client.newTransport(HttpAddress.http1(url));
        RestClient restClient = new RestClient(client, transport);
        RequestBuilder requestBuilder = Request.builder(httpMethod);
        requestBuilder.url(url);
        if (body != null && charset != null) {
            ByteBuf byteBuf = client.getByteBufAllocator().buffer();
            byteBuf.writeCharSequence(body, charset);
            requestBuilder.content(byteBuf);
        }
        transport.execute(requestBuilder.build().setResponseListener(restClient::setResponse)).get();
        return restClient;
    }


}
