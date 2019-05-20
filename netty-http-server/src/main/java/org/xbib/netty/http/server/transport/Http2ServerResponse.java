package org.xbib.netty.http.server.transport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
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
import io.netty.util.AsciiString;
import org.xbib.netty.http.server.ServerName;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;

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
    public HttpResponseStatus getLastStatus() {
        return httpResponseStatus;
    }

    @Override
    public void write(String text) {
        write(HttpResponseStatus.OK, "text/plain; charset=utf-8", text);
    }

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
        write(status, null, (ByteBuf) null);
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
        Http2Headers http2Headers = new DefaultHttp2Headers().status(status.codeAsText()).add(headers);
        Http2HeadersFrame http2HeadersFrame = new DefaultHttp2HeadersFrame(http2Headers,byteBuf == null);
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
                es.append(s, start, i).append(ref);
                start = i + 1;
            }
        }
        return start == 0 ? s : es.append(s.substring(start)).toString();
    }
}
