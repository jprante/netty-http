package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
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
import io.netty.handler.stream.ChunkedNioStream;
import io.netty.util.AsciiString;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

import java.nio.channels.ReadableByteChannel;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Http2ServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(Http2ServerResponse.class.getName());

    private final ServerRequest serverRequest;

    private final ChannelHandlerContext ctx;

    private Http2Headers headers;

    private HttpResponseStatus httpResponseStatus;

    public Http2ServerResponse(ServerRequest serverRequest) {
        Objects.requireNonNull(serverRequest);
        Objects.requireNonNull(serverRequest.getChannelHandlerContext());
        this.serverRequest = serverRequest;
        this.ctx = serverRequest.getChannelHandlerContext();
        this.headers = new DefaultHttp2Headers();
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
        if (byteBuf != null) {
            CharSequence s = headers.get(HttpHeaderNames.CONTENT_TYPE);
            if (s == null) {
                s = contentType != null ? contentType : HttpHeaderValues.APPLICATION_OCTET_STREAM;
                headers.add(HttpHeaderNames.CONTENT_TYPE, s);
            }
            if (!headers.contains(HttpHeaderNames.CONTENT_LENGTH) && !headers.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                int length = byteBuf.readableBytes();
                if (length < 0) {
                    headers.add(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
                } else {
                    headers.add(HttpHeaderNames.CONTENT_LENGTH, Long.toString(length));
                }
            }
            if (serverRequest != null && "close".equalsIgnoreCase(serverRequest.getRequest().headers().get(HttpHeaderNames.CONNECTION)) &&
                    !headers.contains(HttpHeaderNames.CONNECTION)) {
                headers.add(HttpHeaderNames.CONNECTION, "close");
            }
            if (!headers.contains(HttpHeaderNames.DATE)) {
                headers.add(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
            }
            headers.add(HttpHeaderNames.SERVER, ServerName.getServerName());
        }
        if (serverRequest != null) {
            Integer streamId = serverRequest.streamId();
            if (streamId != null) {
                headers.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
            }
        }
        if (ctx.channel().isWritable()) {
            Http2Headers http2Headers = new DefaultHttp2Headers().status(status.codeAsText()).add(headers);
            Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers, byteBuf == null);
            logger.log(Level.FINEST, http2HeadersFrame::toString);
            ctx.channel().write(http2HeadersFrame);
            this.httpResponseStatus = status;
            if (byteBuf != null) {
                Http2DataFrame http2DataFrame = new DefaultHttp2DataFrame(byteBuf, true);
                logger.log(Level.FINEST, http2DataFrame::toString);
                ctx.channel().write(http2DataFrame);
            }
            ctx.channel().flush();
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
            Http2Headers http2Headers = new DefaultHttp2Headers().status(status.codeAsText()).add(headers);
            Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers,false);
            logger.log(Level.FINEST, http2HeadersFrame::toString);
            ctx.channel().write(http2HeadersFrame);
            ChunkedInput<ByteBuf> input = new ChunkedNioStream(byteChannel);
            HttpChunkedInput httpChunkedInput = new HttpChunkedInput(input);
            ChannelFuture channelFuture = ctx.channel().writeAndFlush(httpChunkedInput);
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
