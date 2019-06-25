package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedInput;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.handler.http.HttpPipelinedResponse;

import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(HttpServerResponse.class.getName());

    private final ServerRequest serverRequest;

    private final ChannelHandlerContext ctx;

    private HttpHeaders headers;

    private HttpHeaders trailingHeaders;

    private HttpResponseStatus httpResponseStatus;

    public HttpServerResponse(ServerRequest serverRequest) {
        Objects.requireNonNull(serverRequest, "serverRequest");
        Objects.requireNonNull(serverRequest.getChannelHandlerContext(), "serverRequest channelHandlerContext");
        this.serverRequest = serverRequest;
        this.ctx = serverRequest.getChannelHandlerContext();
        this.headers = new DefaultHttpHeaders();
        this.trailingHeaders = new DefaultHttpHeaders();
    }

    @Override
    public void setHeader(CharSequence name, String value) {
        headers.set(name, value);
    }

    @Override
    public CharSequence getHeader(CharSequence name) {
        return headers.get(name);
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
    public void write(ByteBuf byteBuf) {
        Objects.requireNonNull(byteBuf);
        if (httpResponseStatus == null) {
            httpResponseStatus = HttpResponseStatus.OK;
        }
        CharSequence contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_OCTET_STREAM);
        }
        if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
            int length = byteBuf.readableBytes();
            headers.add(HttpHeaderNames.CONTENT_LENGTH, Long.toString(length));
        }
        if (serverRequest != null && "close".equalsIgnoreCase(serverRequest.getRequest().headers().get(HttpHeaderNames.CONNECTION)) &&
                !headers.contains(HttpHeaderNames.CONNECTION)) {
            headers.add(HttpHeaderNames.CONNECTION, "close");
        }
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }
        headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        if (ctx.channel().isWritable()) {
            FullHttpResponse fullHttpResponse =
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus, byteBuf, headers, trailingHeaders);
            logger.log(Level.FINEST, fullHttpResponse.headers()::toString);
            if (serverRequest != null && serverRequest.getSequenceId() != null) {
                HttpPipelinedResponse httpPipelinedResponse = new HttpPipelinedResponse(fullHttpResponse,
                        ctx.channel().newPromise(), serverRequest.getSequenceId());
                ctx.channel().writeAndFlush(httpPipelinedResponse);
            } else {
                ctx.channel().writeAndFlush(fullHttpResponse);
            }
        } else {
            logger.log(Level.WARNING, "channel not writeable");
        }
    }

    /**
     * Chunked response.
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
            HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, httpResponseStatus);
            httpResponse.headers().add(headers);
            logger.log(Level.FINEST, httpResponse.headers()::toString);
            ctx.channel().write(httpResponse);
            ChannelFuture channelFuture = ctx.channel().writeAndFlush(new HttpChunkedInput(chunkedInput));
            if ("close".equalsIgnoreCase(serverRequest.getRequest().headers().get(HttpHeaderNames.CONNECTION)) &&
                    !headers.contains(HttpHeaderNames.CONNECTION)) {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
        } else {
            logger.log(Level.WARNING, "channel not writeable");
        }
    }
}
