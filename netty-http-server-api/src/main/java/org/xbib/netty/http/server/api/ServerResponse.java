package org.xbib.netty.http.server.api;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.stream.ChunkedInput;
import org.xbib.netty.http.common.cookie.Cookie;
import java.io.Flushable;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

/**
 * HTTP server response.
 */
public interface ServerResponse extends Flushable {

    Builder getBuilder();

    Integer getStreamId();

    Integer getSequenceId();

    Long getResponseId();

    ByteBufOutputStream newOutputStream();

    void flush() throws IOException;

    void write(String content);

    void write(CharBuffer charBuffer, Charset charset);

    void write(byte[] bytes);

    void write(ByteBufOutputStream byteBufOutputStream);

    void write(ByteBuf byteBuf);

    void write(ChunkedInput<ByteBuf> chunkedInput);

    interface Builder {

        Builder setStatus(int statusCode);

        Builder setContentType(CharSequence contentType);

        Builder setCharset(Charset charset);

        Builder setHeader(CharSequence name, String value);

        Builder setTrailingHeader(CharSequence name, String value);

        Builder addCookie(Cookie cookie);

        Builder shouldClose(boolean shouldClose);

        Builder shouldAddServerName(boolean shouldAddServerName);

        Builder setSequenceId(Integer sequenceId);

        Builder setStreamId(Integer streamId);

        Builder setResponseId(Long responseId);

        ServerResponse build();
    }
}
