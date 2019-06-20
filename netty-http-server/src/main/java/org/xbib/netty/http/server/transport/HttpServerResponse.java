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
import io.netty.handler.stream.ChunkedNioStream;
import io.netty.util.AsciiString;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.handler.http.HttpPipelinedResponse;

import java.nio.channels.ReadableByteChannel;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;

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
    public void setHeader(AsciiString name, String value) {
        headers.set(name, value);
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        return ctx;
    }

    @Override
    public HttpResponseStatus getLastStatus() {
        return httpResponseStatus;
    }

    @Override
    public void write(HttpResponseStatus status, String contentType, ByteBuf byteBuf) {
        Objects.requireNonNull(byteBuf);
        CharSequence s = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (s == null) {
            s = contentType != null ? contentType : HttpHeaderValues.APPLICATION_OCTET_STREAM;
            headers.add(HttpHeaderNames.CONTENT_TYPE, s);
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
                    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, byteBuf, headers, trailingHeaders);
            if (serverRequest != null && serverRequest.getSequenceId() != null) {
                HttpPipelinedResponse httpPipelinedResponse = new HttpPipelinedResponse(fullHttpResponse,
                        ctx.channel().newPromise(), serverRequest.getSequenceId());
                ctx.channel().writeAndFlush(httpPipelinedResponse);
            } else {
                ctx.channel().writeAndFlush(fullHttpResponse);
            }
            httpResponseStatus = status;
        } else {
            logger.log(Level.WARNING, "channel not writeable");
        }
    }

    /**
     * Chunked response from a readable byte channel.
     *
     * @param status status
     * @param contentType content type
     * @param byteChannel byte channel
     */
    @Override
    public void write(HttpResponseStatus status, String contentType, ReadableByteChannel byteChannel) {
        CharSequence s = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (s == null) {
            s = contentType != null ? contentType : HttpHeaderValues.APPLICATION_OCTET_STREAM;
            headers.add(HttpHeaderNames.CONTENT_TYPE, s);
        }
        headers.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
        if (!headers.contains(HttpHeaderNames.DATE)) {
            headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        }
        headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        if (ctx.channel().isWritable()) {
            HttpResponse httpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
            httpResponse.headers().add(headers);
            ctx.channel().write(httpResponse);
            logger.log(Level.FINE, "written response " + httpResponse);
            ChunkedInput<ByteBuf> input = new ChunkedNioStream(byteChannel);
            HttpChunkedInput httpChunkedInput = new HttpChunkedInput(input);
            ctx.channel().writeAndFlush(httpChunkedInput);
            ChannelFuture channelFuture = ctx.channel().writeAndFlush(EMPTY_LAST_CONTENT);
            if ("close".equalsIgnoreCase(serverRequest.getRequest().headers().get(HttpHeaderNames.CONNECTION)) &&
                    !headers.contains(HttpHeaderNames.CONNECTION)) {
                channelFuture.addListener(ChannelFutureListener.CLOSE);
            }
            httpResponseStatus = status;
        } else {
            logger.log(Level.WARNING, "channel not writeable");
        }
    }
}
