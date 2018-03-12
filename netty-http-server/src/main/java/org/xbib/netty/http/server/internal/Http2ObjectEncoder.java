package org.xbib.netty.http.server.internal;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.CharSequenceMap;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static io.netty.util.AsciiString.EMPTY_STRING;
import static io.netty.util.ByteProcessor.FIND_SEMI_COLON;

/**
 *
 */
public final class Http2ObjectEncoder extends HttpObjectEncoder {

    /**
     * The set of headers that should not be directly copied when converting headers from HTTP to HTTP/2.
     */
    private static final CharSequenceMap<AsciiString> HTTP_TO_HTTP2_HEADER_BLACKLIST =
            new CharSequenceMap<AsciiString>();
    static {
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.CONNECTION, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString keepAlive = HttpHeaderNames.KEEP_ALIVE;
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(keepAlive, EMPTY_STRING);
        @SuppressWarnings("deprecation")
        AsciiString proxyConnection = HttpHeaderNames.PROXY_CONNECTION;
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(proxyConnection, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.TRANSFER_ENCODING, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.HOST, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpHeaderNames.UPGRADE, EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), EMPTY_STRING);
        HTTP_TO_HTTP2_HEADER_BLACKLIST.add(HttpConversionUtil.ExtensionHeaderNames.PATH.text(), EMPTY_STRING);
    }

    private final Http2ConnectionEncoder encoder;

    public Http2ObjectEncoder(Http2ConnectionEncoder encoder) {
        super();
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    @Override
    protected ChannelFuture doWriteHeaders(ChannelHandlerContext ctx, int id, int streamId,
                                           HttpHeaders headers, HttpResponseStatus status,
                                           boolean endStream) {
        final ChannelFuture future = validateStream(ctx, streamId);
        if (future != null) {
            return future;
        }
        Http2Headers http2Headers = toHttp2Headers(headers, status, false);
        return encoder.writeHeaders(ctx, streamId, http2Headers, 0, endStream, ctx.newPromise());
    }

    @Override
    protected ChannelFuture doWriteData(ChannelHandlerContext ctx, int id, int streamId, ByteBuf data,
                                        boolean endStream) {
        final ChannelFuture future = validateStream(ctx, streamId);
        if (future != null) {
            return future;
        }
        return encoder.writeData(ctx, streamId, data, 0, endStream, ctx.newPromise());
    }

    @Override
    protected ChannelFuture doWriteReset(ChannelHandlerContext ctx, int id, int streamId, Http2Error error) {
        final ChannelFuture future = validateStream(ctx, streamId);
        if (future != null) {
            return future;
        }
        return encoder.writeRstStream(ctx, streamId, error.code(), ctx.newPromise());
    }


    @Override
    protected void doClose() {
    }

    private ChannelFuture validateStream(ChannelHandlerContext ctx, int streamId) {
        final Http2Stream stream = encoder.connection().stream(streamId);
        if (stream != null) {
            switch (stream.state()) {
                case RESERVED_LOCAL:
                case OPEN:
                case HALF_CLOSED_REMOTE:
                    break;
                default:
                    return ctx.newFailedFuture(new IllegalStateException("stream state = " + stream.state().name()));
            }
        } else if (encoder.connection().streamMayHaveExisted(streamId)) {
            return ctx.newFailedFuture(new IllegalStateException("stream may have existed"));
        }
        return null;
    }

    public static Http2Headers toHttp2Headers(HttpHeaders inHeaders,
                                              HttpResponseStatus status,
                                              boolean validateHeaders) {
        final Http2Headers out = new DefaultHttp2Headers(validateHeaders, inHeaders.size());
        out.status(status.codeAsText());
        toHttp2Headers(inHeaders, out);
        return out;
    }

    public static void toHttp2Headers(HttpHeaders inHeaders, Http2Headers outHeaders) {
        Iterator<Map.Entry<CharSequence, CharSequence>> iter = inHeaders.iteratorCharSequence();
        while (iter.hasNext()) {
            Map.Entry<CharSequence, CharSequence> entry = iter.next();
            final AsciiString aName = AsciiString.of(entry.getKey()).toLowerCase();
            if (!HTTP_TO_HTTP2_HEADER_BLACKLIST.contains(aName)) {
                // https://tools.ietf.org/html/rfc7540#section-8.1.2.2 makes a special exception for TE
                if (aName.contentEqualsIgnoreCase(HttpHeaderNames.TE) &&
                        !AsciiString.contentEqualsIgnoreCase(entry.getValue(), HttpHeaderValues.TRAILERS)) {
                    throw new IllegalArgumentException("Invalid value for " + HttpHeaderNames.TE + ": " +
                            entry.getValue());
                }
                if (aName.contentEqualsIgnoreCase(HttpHeaderNames.COOKIE)) {
                    AsciiString value = AsciiString.of(entry.getValue());
                    // split up cookies to allow for better compression
                    // https://tools.ietf.org/html/rfc7540#section-8.1.2.5
                    try {
                        int index = value.forEachByte(FIND_SEMI_COLON);
                        if (index != -1) {
                            int start = 0;
                            do {
                                outHeaders.add(HttpHeaderNames.COOKIE, value.subSequence(start, index, false));
                                // skip 2 characters "; " (see https://tools.ietf.org/html/rfc6265#section-4.2.1)
                                start = index + 2;
                            } while (start < value.length() &&
                                    (index = value.forEachByte(start, value.length() - start, FIND_SEMI_COLON)) != -1);
                            if (start >= value.length()) {
                                throw new IllegalArgumentException("cookie value is of unexpected format: " + value);
                            }
                            outHeaders.add(HttpHeaderNames.COOKIE, value.subSequence(start, value.length(), false));
                        } else {
                            outHeaders.add(HttpHeaderNames.COOKIE, value);
                        }
                    } catch (Exception e) {
                        // This is not expect to happen because FIND_SEMI_COLON never throws but must be caught
                        // because of the ByteProcessor interface.
                        throw new IllegalStateException(e);
                    }
                } else {
                    outHeaders.add(aName, entry.getValue());
                }
            }
        }
    }
}


