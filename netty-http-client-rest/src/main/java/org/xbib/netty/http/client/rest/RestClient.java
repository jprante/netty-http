package org.xbib.netty.http.client.rest;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import org.xbib.net.URL;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.common.HttpResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class RestClient {

    private HttpResponse response;

    private ByteBuf byteBuf;

    private RestClient() {
    }

    private void setResponse(HttpResponse response) {
        this.response = response;
        this.byteBuf = response != null ? response.getBody().retain() : null;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public String asString() {
        return asString(StandardCharsets.UTF_8);
    }

    private String asString(Charset charset) {
        return byteBuf != null && byteBuf.isReadable() ? byteBuf.toString(charset) : null;
    }

    public static RestClient get(String urlString) throws IOException {
        return method(urlString, HttpMethod.GET);
    }

    public static RestClient delete(String urlString) throws IOException {
        return method(urlString, HttpMethod.DELETE);
    }

    public static RestClient post(String urlString, String body) throws IOException {
        return method(urlString, body, StandardCharsets.UTF_8, HttpMethod.POST);
    }

    public static RestClient put(String urlString, String body) throws IOException {
        return method(urlString, body, StandardCharsets.UTF_8, HttpMethod.PUT);
    }

    private static RestClient method(String urlString, HttpMethod httpMethod) throws IOException {
        return method(urlString, null, null, httpMethod);
    }

    private static RestClient method(String urlString,
                                     String body, Charset charset,
                                     HttpMethod httpMethod) throws IOException {
        URL url = URL.create(urlString);
        RestClient restClient = new RestClient();
        try (Client client = Client.builder()
                .setThreadCount(2) // for redirect
                .build()) {
            Request.Builder requestBuilder = Request.builder(httpMethod).url(url);
            if (body != null) {
                ByteBuf byteBuf = client.getByteBufAllocator().buffer();
                byteBuf.writeCharSequence(body, charset);
                requestBuilder.content(byteBuf);
            }
            client.newTransport(HttpAddress.http1(url))
                    .execute(requestBuilder.setResponseListener(restClient::setResponse).build())
                    .close();
        } catch (Exception e) {
            throw new IOException(e);
        }
        return restClient;
    }
}
