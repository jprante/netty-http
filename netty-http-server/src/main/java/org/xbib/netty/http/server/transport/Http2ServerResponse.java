package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
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
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.api.ServerRequest;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.cookie.ServerCookieEncoder;

import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(Http2ServerResponse.class.getName());

    private final Server server;

    private final ServerRequest serverRequest;

    private final ChannelHandlerContext ctx;

    private Http2Headers headers;

    private HttpResponseStatus httpResponseStatus;

    Http2ServerResponse(Server server, HttpServerRequest serverRequest, ChannelHandlerContext ctx) {
        this.server = server;
        this.serverRequest = serverRequest;
        this.ctx = ctx;
        this.headers = new DefaultHttp2Headers();
    }

    @Override
    public ServerResponse withHeader(CharSequence name, String value) {
        headers.set(name, value);
        return this;
    }

    @Override
    public ServerResponse withCookie(Cookie cookie) {
        Objects.requireNonNull(cookie);
        headers.add(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
        return this;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    @Override
    public HttpResponseStatus getStatus() {
        return httpResponseStatus;
    }

    @Override
    public ServerResponse withStatus(HttpResponseStatus httpResponseStatus) {
        this.httpResponseStatus = httpResponseStatus;
        return this;
    }

    @Override
    public ServerResponse withContentType(String contentType) {
        headers.remove(HttpHeaderNames.CONTENT_TYPE);
        headers.add(HttpHeaderNames.CONTENT_TYPE, contentType);
        return this;
    }

    @Override
    public ServerResponse withCharset(Charset charset) {
        CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType != null) {
            headers.remove(HttpHeaderNames.CONTENT_TYPE);
            headers.add(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=" + charset.name());
        }
        return this;
    }

    @Override
    public ByteBufOutputStream getOutputStream() {
        return new ByteBufOutputStream(ctx.alloc().buffer());
    }

    @Override
    public void flush() {
        write((ByteBuf) null);
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
        if (httpResponseStatus == null) {
            httpResponseStatus = HttpResponseStatus.OK;
        }
        CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        }
        if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            if (byteBuf != null) {
                headers.add(HttpHeaderNames.CONTENT_LENGTH, Long.toString(byteBuf.readableBytes()));
            }
        }
        if (serverRequest != null && "close".equalsIgnoreCase(serverRequest.getHeaders().get(HttpHeaderNames.CONNECTION)) &&
                !headers.contains(HttpHeaderNames.CONNECTION)) {
            headers.add(HttpHeaderNames.CONNECTION, "close");
        }
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }
        headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        if (serverRequest != null) {
            Integer streamId = serverRequest.getStreamId();
            if (streamId != null) {
                headers.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
            }
        }
        if (ctx.channel().isWritable()) {
            Http2Headers http2Headers = new DefaultHttp2Headers().status(httpResponseStatus.codeAsText()).add(headers);
            Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers, byteBuf == null);
            ctx.channel().write(http2HeadersFrame);
            if (byteBuf != null) {
                Http2DataFrame http2DataFrame = new DefaultHttp2DataFrame(byteBuf, true);
                ctx.channel().write(http2DataFrame);
            }
            ctx.channel().flush();
            server.getResponseCounter().incrementAndGet();
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
        if (httpResponseStatus == null) {
            httpResponseStatus = HttpResponseStatus.OK;
        }
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
            if ("close".equalsIgnoreCase(serverRequest.getHeaders().get(HttpHeaderNames.CONNECTION)) &&
                    !headers.contains(HttpHeaderNames.CONNECTION)) {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
            server.getResponseCounter().incrementAndGet();
        } else {
            logger.log(Level.WARNING, "channel is not writeable: " + ctx.channel());
        }
    }
}
