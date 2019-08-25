package org.xbib.netty.http.common;

import io.netty.buffer.ByteBuf;

import java.io.InputStream;
import java.nio.charset.Charset;

public interface HttpResponse {

    HttpAddress getAddress();

    HttpStatus getStatus();

    HttpHeaders getHeaders();

    ByteBuf getBody();

    InputStream getBodyAsStream();

    String getBodyAsString(Charset charset);

    void release();
}
