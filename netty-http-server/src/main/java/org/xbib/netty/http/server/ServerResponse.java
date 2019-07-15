package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedInput;
import org.xbib.netty.http.common.cookie.Cookie;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * HTTP server response.
 */
public interface ServerResponse {

    ChannelHandlerContext getChannelHandlerContext();

    HttpResponseStatus getStatus();

    ServerResponse withStatus(HttpResponseStatus httpResponseStatus);

    ServerResponse withHeader(CharSequence name, String value);

    ServerResponse withContentType(String contentType);

    ServerResponse withCharset(Charset charset);

    ServerResponse withCookie(Cookie cookie);

    ByteBufOutputStream getOutputStream();

    void flush();

    void write(byte[] bytes);

    void write(ByteBufOutputStream byteBufOutputStream);

    void write(ByteBuf byteBuf);

    void write(ChunkedInput<ByteBuf> chunkedInput);

    /**
     * Convenience methods.
     */

    static void write(ServerResponse serverResponse, int status) {
        write(serverResponse, HttpResponseStatus.valueOf(status));
    }

    static void write(ServerResponse serverResponse, HttpResponseStatus status) {
        write(serverResponse, status, "application/octet-stream", status.reasonPhrase());
    }

    /**
     * Responses to  a HEAD request.
     * @param serverResponse server response
     * @param status status
     * @param contentType content-type as if it were for a GET request (RFC 2616)
     */
    static void write(ServerResponse serverResponse, HttpResponseStatus status, String contentType) {
        write(serverResponse, status, contentType, EMPTY_STRING);
    }

    static void write(ServerResponse serverResponse, String text) {
        write(serverResponse, HttpResponseStatus.OK, "text/plain", text);
    }

    static void write(ServerResponse serverResponse, HttpResponseStatus status, String contentType, String text) {
        serverResponse.withStatus(status)
                .withContentType(contentType)
                .withCharset(StandardCharsets.UTF_8)
                .write(ByteBufUtil.writeUtf8(serverResponse.getChannelHandlerContext().alloc(), text));
    }

    static void write(ServerResponse serverResponse,
                             HttpResponseStatus status, String contentType, String text, Charset charset) {
        write(serverResponse, status, contentType, CharBuffer.allocate(text.length()).append(text), charset);
    }

    static void write(ServerResponse serverResponse, HttpResponseStatus status, String contentType,
                      CharBuffer charBuffer, Charset charset) {
        serverResponse.withStatus(status)
                .withContentType(contentType)
                .withCharset(charset)
                .write(ByteBufUtil.encodeString(serverResponse.getChannelHandlerContext().alloc(), charBuffer, charset));
    }

    String EMPTY_STRING = "";
}
