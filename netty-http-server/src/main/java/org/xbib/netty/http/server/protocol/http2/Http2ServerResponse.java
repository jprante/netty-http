package org.xbib.netty.http.server.protocol.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.stream.ChunkedInput;
import org.xbib.netty.http.common.cookie.Cookie;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.cookie.ServerCookieEncoder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(Http2ServerResponse.class.getName());

    private final Builder builder;

    private final ChannelHandlerContext ctx;

    private final Http2Headers headers;

    private final Http2Headers trailingHeaders;

    private final HttpResponseStatus httpResponseStatus;

    private final boolean shouldClose;

    private final boolean shouldAddServerName;

    private final Integer sequenceId;

    private final Integer streamId;

    private final Long responseId;

    private final CharSequence contentType;

    private Http2ServerResponse(Builder builder) {
        this.builder = builder;
        this.ctx = builder.ctx;
        this.headers = builder.headers;
        this.trailingHeaders = builder.trailingHeaders;
        this.httpResponseStatus = HttpResponseStatus.valueOf(builder.statusCode);
        this.shouldClose = builder.shouldClose;
        this.shouldAddServerName = builder.shouldAddServerName;
        this.sequenceId = builder.sequenceId;
        this.streamId = builder.streamId;
        this.responseId = builder.responseId;
        this.contentType = builder.contentType;
    }

    @Override
    public Builder getBuilder() {
        return builder;
    }

    @Override
    public Integer getStreamId() {
        return streamId;
    }

    @Override
    public Integer getSequenceId() {
        return sequenceId;
    }

    @Override
    public Long getResponseId() {
        return responseId;
    }

    public static Builder builder(ChannelHandlerContext ctx) {
        return new Builder(ctx);
    }

    @Override
    public ByteBufOutputStream newOutputStream() {
        return new ByteBufOutputStream(ctx.alloc().buffer());
    }

    @Override
    public void flush() {
        write((ByteBuf) null);
    }

    @Override
    public void write(String string) {
        write(ByteBufUtil.writeUtf8(ctx.alloc(), string));
    }

    @Override
    public void write(CharBuffer charBuffer, Charset charset) {
        write(ByteBufUtil.encodeString(ctx.alloc(), charBuffer, charset));
    }

    @Override
    public void write(byte[] bytes) {
        ByteBuf byteBuf = ctx.alloc().buffer(bytes.length);
        byteBuf.writeBytes(bytes);
        write(byteBuf);
    }

    @Override
    public void write(ByteBufOutputStream byteBufOutputStream) {
        write(byteBufOutputStream.buffer());
    }

    @Override
    public void write(ByteBuf byteBuf) {
        headers.add(HttpHeaderNames.CONTENT_TYPE, contentType);
        if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            if (byteBuf != null) {
                headers.add(HttpHeaderNames.CONTENT_LENGTH, Long.toString(byteBuf.readableBytes()));
            }
        }
        if (shouldClose) {
            headers.add(HttpHeaderNames.CONNECTION, "close");
        }
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }
        if (shouldAddServerName) {
            headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        }
        if (streamId != null) {
            headers.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
        }
        if (ctx.channel().isWritable()) {
            Http2Headers http2Headers = new DefaultHttp2Headers().status(httpResponseStatus.codeAsText()).add(headers);
            Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers, byteBuf == null);
            ctx.channel().write(http2HeadersFrame);
            if (byteBuf != null) {
                Http2DataFrame http2DataFrame = new DefaultHttp2DataFrame(byteBuf, true);
                ctx.channel().write(http2DataFrame);
            }
            /*if (trailingHeaders != null) {
                Http2Headers trailingHttp2Headers = new DefaultHttp2Headers().add(trailingHeaders);
                Http2HeadersFrame trailingHttp2HeadersFrame = new DefaultHttp2HeadersFrame(trailingHttp2Headers, byteBuf == null);
                ctx.channel().write(trailingHttp2HeadersFrame);
            }*/
            ctx.channel().flush();
        } else {
            logger.log(Level.WARNING, "channel is not writeable: " + ctx.channel());
        }
    }

    /**
     * Chunked response from a readable byte channel.
     *
     * @param chunkedInput chunked input
     */
    @Override
    public void write(ChunkedInput<ByteBuf> chunkedInput) {
        Objects.requireNonNull(chunkedInput);
        CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        }
        headers.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }
        headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        if (ctx.channel().isWritable()) {
            Http2Headers http2Headers = new DefaultHttp2Headers().status(httpResponseStatus.codeAsText()).add(headers);
            Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers,false);
            ctx.channel().write(http2HeadersFrame);
            ChannelFuture channelFuture = ctx.channel().writeAndFlush(new HttpChunkedInput(chunkedInput));
            if (shouldClose) {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            logger.log(Level.WARNING, "channel is not writeable: " + ctx.channel());
        }
    }

    public static class Builder implements ServerResponse.Builder {

        private final ChannelHandlerContext ctx;

        private final Http2Headers headers;

        private final Http2Headers trailingHeaders;

        private int statusCode;

        private boolean shouldClose;

        private boolean shouldAddServerName;

        private Integer sequenceId;

        private Integer streamId;

        private Long responseId;

        private CharSequence contentType;

        private Builder(ChannelHandlerContext ctx) {
            this.ctx = ctx;
            this.statusCode = HttpResponseStatus.OK.code();
            this.headers = new DefaultHttp2Headers();
            this.trailingHeaders = new DefaultHttp2Headers();
            this.contentType = HttpHeaderValues.APPLICATION_OCTET_STREAM;
        }

        @Override
        public Builder setStatus(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        @Override
        public Builder setContentType(CharSequence contentType) {
            this.contentType = contentType;
            return this;
        }

        @Override
        public Builder setHeader(CharSequence name, String value) {
            headers.set(name, value);
            return this;
        }

        @Override
        public Builder setTrailingHeader(CharSequence name, String value) {
            trailingHeaders.set(name, value);
            return this;
        }

        @Override
        public Builder setCharset(Charset charset) {
            if (contentType != null) {
                this.contentType = contentType + "; charset=" + charset.name();
            }
            return this;
        }

        @Override
        public Builder addCookie(Cookie cookie) {
            Objects.requireNonNull(cookie);
            headers.add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
            return this;
        }

        @Override
        public Builder shouldClose(boolean shouldClose) {
            this.shouldClose = shouldClose;
            return this;
        }

        @Override
        public Builder shouldAddServerName(boolean shouldAddServerName) {
            this.shouldAddServerName = shouldAddServerName;
            return this;
        }

        @Override
        public Builder setSequenceId(Integer sequenceId) {
            this.sequenceId = sequenceId;
            return this;
        }

        @Override
        public Builder setStreamId(Integer streamId) {
            this.streamId = streamId;
            return this;
        }

        @Override
        public Builder setResponseId(Long responseId) {
            this.responseId = responseId;
            return this;
        }

        @Override
        public ServerResponse build() {
            return new Http2ServerResponse(this);
        }
    }
}
