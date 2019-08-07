package org.xbib.netty.http.client.rest;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RestClient {

    private static final Client client = new Client();

    private HttpResponse response;

    private RestClient() {
    }

    public void setResponse(HttpResponse response) {
        this.response = response;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public String asString() {
        return asString(StandardCharsets.UTF_8);
    }

    public String asString(Charset charset) {
        ByteBuf byteBuf = response != null ? response.getBody() : null;
        return byteBuf != null && byteBuf.isReadable() ? byteBuf.toString(charset) : null;
    }

    public void close() throws IOException {
        client.shutdownGracefully();
    }

    public static RestClient get(String urlString) throws IOException {
        return method(urlString, null, null, HttpMethod.GET);
    }

    public static RestClient delete(String urlString) throws IOException {
        return method(urlString, null, null, HttpMethod.DELETE);
    }

    public static RestClient post(String urlString, String body) throws IOException {
        return method(urlString, body, StandardCharsets.UTF_8, HttpMethod.POST);
    }

    public static RestClient post(String urlString, ByteBuf content) throws IOException {
        return method(urlString, content, HttpMethod.POST);
    }

    public static RestClient put(String urlString, String body) throws IOException {
        return method(urlString, body, StandardCharsets.UTF_8, HttpMethod.PUT);
    }

    public static RestClient put(String urlString, ByteBuf content) throws IOException {
        return method(urlString, content, HttpMethod.PUT);
    }

    public static RestClient method(String urlString,
                                    String body, Charset charset,
                                    HttpMethod httpMethod) throws IOException {
        ByteBuf byteBuf = null;
        if (body != null && charset != null) {
            byteBuf = client.getByteBufAllocator().buffer();
            byteBuf.writeCharSequence(body, charset);
        }
        return method(urlString, byteBuf, httpMethod);
    }

    public static RestClient method(String urlString,
                                    ByteBuf byteBuf,
                                    HttpMethod httpMethod) throws IOException {
        URL url = URL.create(urlString);
        RestClient restClient = new RestClient();
        Request.Builder requestBuilder = Request.builder(httpMethod).url(url);
        if (byteBuf != null) {
            requestBuilder.content(byteBuf);
        }
        client.newTransport(HttpAddress.http1(url))
                .execute(requestBuilder.build().setResponseListener(restClient::setResponse)).get();
        return restClient;
    }
}
