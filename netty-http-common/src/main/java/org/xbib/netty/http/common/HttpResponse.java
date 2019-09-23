package org.xbib.netty.http.common;

import io.netty.buffer.ByteBuf;

import org.xbib.netty.http.common.cookie.CookieBox;
import java.io.InputStream;
import java.nio.charset.Charset;

public interface HttpResponse {

    HttpAddress getAddress();

    HttpStatus getStatus();

    HttpHeaders getHeaders();

    CookieBox getCookies();

    ByteBuf getBody();

    InputStream getBodyAsStream();

    String getBodyAsString(Charset charset);

    void release();
}
