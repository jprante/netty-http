package org.xbib.netty.http.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.http.FullHttpResponse;

import org.xbib.netty.http.common.cookie.CookieBox;
import java.io.InputStream;
import java.nio.charset.Charset;

public class DefaultHttpResponse implements HttpResponse {

    private final HttpAddress httpAddress;

    private final FullHttpResponse fullHttpResponse;

    private final HttpStatus httpStatus;

    private final HttpHeaders httpHeaders;

    private final CookieBox cookieBox;

    public DefaultHttpResponse(HttpAddress httpAddress,
                               FullHttpResponse fullHttpResponse,
                               CookieBox cookieBox) {
        this.httpAddress = httpAddress;
        this.fullHttpResponse = fullHttpResponse.retain();
        this.httpStatus = new HttpStatus(this.fullHttpResponse.status());
        this.httpHeaders = new DefaultHttpHeaders(this.fullHttpResponse.headers());
        this.cookieBox = cookieBox;
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
    public CookieBox getCookies() {
        return cookieBox;
    }

    @Override
    public ByteBuf getBody() {
        return fullHttpResponse.content();
    }

    @Override
    public InputStream getBodyAsStream() {
        return new ByteBufInputStream(getBody());
    }

    @Override
    public String getBodyAsString(Charset charset) {
        return getBody().toString(charset);
    }

    @Override
    public void release() {
        this.fullHttpResponse.release();
    }
}
