package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders;

import java.nio.charset.Charset;

/**
 * HTTP server response.
 */
public interface ServerResponse {

    HttpHeaders getHeaders();

    void write(String text);

    void writeError(int status);

    void writeError(int status, String text);

    void write(int status);

    void write(int status, String contentType, String text);

    void write(int status, String contentType, String text, Charset charset);

    void write(int status, String contentType, ByteBuf byteBuf);

}
