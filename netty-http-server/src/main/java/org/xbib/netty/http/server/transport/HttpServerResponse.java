package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.handler.http.HttpPipelinedResponse;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(HttpServerResponse.class.getName());

    private static final String EMPTY_STRING = "";

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
    public HttpResponseStatus getLastStatus() {
        return httpResponseStatus;
    }

    @Override
    public void write(String text) {
        write(HttpResponseStatus.OK, "text/plain; charset=utf-8", text);
    }

    /**
     * Sends an error response with the given status and default body.
     *
     * @param status the response status
     */
    @Override
    public void writeError(HttpResponseStatus status) {
        writeError(status, status.reasonPhrase());
    }

    /**
     * Sends an error response with the given status and detailed message.
     *
     * @param status the response status
     * @param text   the text body
     */
    @Override
    public void writeError(HttpResponseStatus status, String text) {
        write(status, "text/plain; charset=utf-8", status.code() + " " + text);
    }

    @Override
    public void write(HttpResponseStatus status) {
        write(status, "application/octet-stream", EMPTY_STRING);
    }

    @Override
    public void write(HttpResponseStatus status, String contentType, String text) {
        write(status, contentType, ByteBufUtil.writeUtf8(ctx.alloc(), text));
    }

    @Override
    public void write(HttpResponseStatus status, String contentType, String text, Charset charset) {
        write(status, contentType, ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.allocate(text.length()).append(text), charset));
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
        FullHttpResponse fullHttpResponse =
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, byteBuf, headers, trailingHeaders);
        if (serverRequest != null && serverRequest.getSequenceId() != null) {
            HttpPipelinedResponse httpPipelinedResponse = new HttpPipelinedResponse(fullHttpResponse,
                    ctx.channel().newPromise(), serverRequest.getSequenceId());
            if (ctx.channel().isWritable()) {
                logger.log(Level.FINEST, fullHttpResponse::toString);
                ctx.channel().writeAndFlush(httpPipelinedResponse);
                httpResponseStatus = status;
            } else {
                logger.log(Level.WARNING, "channel not writeable");
            }
        } else {
            if (ctx.channel().isWritable()) {
                logger.log(Level.FINEST, fullHttpResponse::toString);
                ctx.channel().writeAndFlush(fullHttpResponse);
                httpResponseStatus = status;
            } else {
                logger.log(Level.WARNING, "channel not writeable");
            }
        }
    }
}
