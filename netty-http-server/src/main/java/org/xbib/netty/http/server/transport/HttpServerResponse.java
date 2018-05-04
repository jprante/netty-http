package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
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
import org.xbib.netty.http.server.handler.http.HttpPipelinedResponse;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

public class HttpServerResponse implements ServerResponse {

    private static final Logger logger = Logger.getLogger(HttpServerResponse.class.getName());

    private final ServerRequest serverRequest;

    private final ChannelHandlerContext ctx;

    private HttpHeaders headers;

    private HttpHeaders trailingHeaders;

    public HttpServerResponse(ServerRequest serverRequest, ChannelHandlerContext ctx) {
        this.serverRequest = serverRequest;
        this.ctx = ctx;
        this.headers = new DefaultHttpHeaders();
        this.trailingHeaders = new DefaultHttpHeaders();
    }

    @Override
    public void setHeader(AsciiString name, String value) {
        headers.set(name, value);
    }

    @Override
    public void write(String text) {
        write(200, "text/plain; charset=utf-8", text);
    }

    /**
     * Sends an error response with the given status and default body.
     *
     * @param status the response status
     */
    @Override
    public void writeError(int status) {
        writeError(status, status < 400 ? ":)" : "sorry it didn't work out :(");
    }

    /**
     * Sends an error response with the given status and detailed message.
     * An HTML body is created containing the status and its description,
     * as well as the message, which is escaped using the
     * {@link #escapeHTML escape} method.
     *
     * @param status the response status
     * @param text   the text body (sent as text/html)
     */
    @Override
    public void writeError(int status, String text) {
        write(status, "text/html; charset=utf-8",
                String.format("<!DOCTYPE html>%n<html>%n<head><title>%d %s</title></head>%n" +
                                "<body><h1>%d %s</h1>%n<p>%s</p>%n</body></html>",
                        status, HttpResponseStatus.valueOf(status).reasonPhrase(),
                        status, HttpResponseStatus.valueOf(status).reasonPhrase(),
                        escapeHTML(text)));
    }

    @Override
    public void write(int status) {
        write(status, null, (ByteBuf) null);
    }

    @Override
    public void write(int status, String contentType, String text) {
        write(status, contentType, ByteBufUtil.writeUtf8(ctx.alloc(), text));
    }

    @Override
    public void write(int status, String contentType, String text, Charset charset) {
        write(status, contentType, ByteBufUtil.encodeString(ctx.alloc(), CharBuffer.allocate(text.length()).append(text), charset));
    }

    @Override
    public void write(int status, String contentType, ByteBuf byteBuf) {
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
        FullHttpResponse fullHttpResponse = byteBuf != null ?
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(status), byteBuf, headers, trailingHeaders) :
                new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(status), Unpooled.EMPTY_BUFFER, headers, trailingHeaders);
        if (serverRequest != null && serverRequest.getSequenceId() != null) {
            HttpPipelinedResponse httpPipelinedResponse = new HttpPipelinedResponse(fullHttpResponse,
                    ctx.channel().newPromise(), serverRequest.getSequenceId());
            if (ctx.channel().isWritable()) {
                ctx.channel().writeAndFlush(httpPipelinedResponse);
            }
        } else {
            if (ctx.channel().isWritable()) {
                ctx.channel().writeAndFlush(fullHttpResponse);
            }
        }
    }

    /**
     * Returns an HTML-escaped version of the given string for safe display
     * within a web page. The characters '&amp;', '&gt;' and '&lt;' must always
     * be escaped, and single and double quotes must be escaped within
     * attribute values; this method escapes them always. This method can
     * be used for generating both HTML and XHTML valid content.
     *
     * @param s the string to escape
     * @return the escaped string
     * @see <a href="http://www.w3.org/International/questions/qa-escapes">The W3C FAQ</a>
     */
    private static String escapeHTML(String s) {
        int len = s.length();
        StringBuilder es = new StringBuilder(len + 30);
        int start = 0;
        for (int i = 0; i < len; i++) {
            String ref = null;
            switch (s.charAt(i)) {
                case '&':
                    ref = "&amp;";
                    break;
                case '>':
                    ref = "&gt;";
                    break;
                case '<':
                    ref = "&lt;";
                    break;
                case '"':
                    ref = "&quot;";
                    break;
                case '\'':
                    ref = "&#39;";
                    break;
                default:
                    break;
            }
            if (ref != null) {
                es.append(s.substring(start, i)).append(ref);
                start = i + 1;
            }
        }
        return start == 0 ? s : es.append(s.substring(start)).toString();
    }
}
