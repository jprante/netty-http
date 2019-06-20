package org.xbib.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;

import java.nio.CharBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;

/**
 * HTTP server response.
 */
public interface ServerResponse {

    void setHeader(AsciiString name, String value);

    ChannelHandlerContext getChannelHandlerContext();

    HttpResponseStatus getLastStatus();

    void write(HttpResponseStatus status, String contentType, ByteBuf byteBuf);

    void write(HttpResponseStatus status, String contentType, ReadableByteChannel byteChannel);

    static void write(ServerResponse serverResponse, HttpResponseStatus status) {
        write(serverResponse, status, status.reasonPhrase());
    }

    static void write(ServerResponse serverResponse, String text) {
        write(serverResponse, HttpResponseStatus.OK, text);
    }

    static void write(ServerResponse serverResponse, HttpResponseStatus status, String text) {
        write(serverResponse, status, "text/plain; charset=utf-8", text);
    }

    static void write(ServerResponse serverResponse,
                             HttpResponseStatus status, String contentType, String text) {
        serverResponse.write(status, contentType,
                ByteBufUtil.writeUtf8(serverResponse.getChannelHandlerContext().alloc(), text));
    }

    static void write(ServerResponse serverResponse,
                             HttpResponseStatus status, String contentType, String text, Charset charset) {
        serverResponse.write(status, contentType,
                ByteBufUtil.encodeString(serverResponse.getChannelHandlerContext().alloc(),
                        CharBuffer.allocate(text.length()).append(text), charset));
    }

}
