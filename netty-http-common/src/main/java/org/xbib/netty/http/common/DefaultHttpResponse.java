package org.xbib.netty.http.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpResponse;

import java.io.InputStream;
import java.nio.charset.Charset;

public class DefaultHttpResponse implements HttpResponse {

    private final HttpAddress httpAddress;

    private final FullHttpResponse fullHttpResponse;

    private final HttpStatus httpStatus;

    private final HttpHeaders httpHeaders;

    public DefaultHttpResponse(HttpAddress httpAddress, FullHttpResponse fullHttpResponse) {
        this.httpAddress = httpAddress;
        this.fullHttpResponse = fullHttpResponse;
        this.httpStatus = new HttpStatus(fullHttpResponse.status());
        this.httpHeaders = new DefaultHttpHeaders(fullHttpResponse.headers());
    }

    @Override
    public HttpAddress getAddress() {
        return httpAddress;
    }

    @Override
    public HttpStatus getStatus() {
        return httpStatus;
    }

    @Override
    public HttpHeaders getHeaders() {
        return httpHeaders;
    }

    @Override
    public ByteBuf getBody() {
        return fullHttpResponse.content().asReadOnly();
    }

    @Override
    public InputStream getBodyAsStream() {
        return new ByteBufInputStream(getBody());
    }

    @Override
    public String getBodyAsString(Charset charset) {
        return getBody().toString(charset);
    }
}
