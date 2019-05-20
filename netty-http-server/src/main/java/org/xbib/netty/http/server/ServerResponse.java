package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;

import java.nio.charset.Charset;

/**
 * HTTP server response.
 */
public interface ServerResponse {

    void setHeader(AsciiString name, String value);

    HttpResponseStatus getLastStatus();

    void write(String text);

    void writeError(HttpResponseStatus status);

    void writeError(HttpResponseStatus status, String text);

    void write(HttpResponseStatus status);

    void write(HttpResponseStatus status, String contentType, String text);

    void write(HttpResponseStatus status, String contentType, String text, Charset charset);

    void write(HttpResponseStatus status, String contentType, ByteBuf byteBuf);

}
