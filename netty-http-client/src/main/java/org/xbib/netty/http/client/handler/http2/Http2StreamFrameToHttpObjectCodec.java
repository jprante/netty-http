package org.xbib.netty.http.client.handler.http2;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2MultiplexCodec;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamFrame;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.internal.UnstableApi;

import java.util.List;

/**
 * This handler converts from {@link Http2StreamFrame} to {@link HttpObject},
 * and back. It can be used as an adapter in conjunction with {@link
 * Http2MultiplexCodec} to make http/2 connections backward-compatible with
 * {@link ChannelHandler}s expecting {@link HttpObject}.
 *
 * For simplicity, it converts to chunked encoding unless the entire stream
 * is a single header.
 *
 * Patched version of original Netty's Http2StreamFrameToHttpObjectCodec.
 * This one is using the streamId from {@code frame.stream().id()}.
 */
@UnstableApi
@Sharable
public class Http2StreamFrameToHttpObjectCodec extends MessageToMessageCodec<Http2StreamFrame, HttpObject> {

    private final boolean isServer;

    private final boolean validateHeaders;

    private HttpScheme scheme;

    public Http2StreamFrameToHttpObjectCodec(final boolean isServer,
                                             final boolean validateHeaders) {
        this.isServer = isServer;
        this.validateHeaders = validateHeaders;
        scheme = HttpScheme.HTTP;
    }

    public Http2StreamFrameToHttpObjectCodec(final boolean isServer) {
        this(isServer, true);
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return (msg instanceof Http2HeadersFrame) || (msg instanceof Http2DataFrame);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Http2StreamFrame frame, List<Object> out) throws Exception {
        if (frame instanceof Http2HeadersFrame) {
            int id = frame.stream() != null ? frame.stream().id() : -1;
            Http2HeadersFrame headersFrame = (Http2HeadersFrame) frame;
            Http2Headers headers = headersFrame.headers();

            final CharSequence status = headers.status();

            // 100-continue response is a special case where Http2HeadersFrame#isEndStream=false
            // but we need to decode it as a FullHttpResponse to play nice with HttpObjectAggregator.
            if (null != status && HttpResponseStatus.CONTINUE.codeAsText().contentEquals(status)) {
                final FullHttpMessage fullMsg = newFullMessage(id, headers, ctx.alloc());
                out.add(fullMsg);
                return;
            }

            if (headersFrame.isEndStream()) {
                if (headers.method() == null && status == null) {
                    LastHttpContent last = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
                    HttpConversionUtil.addHttp2ToHttpHeaders(id, headers, last.trailingHeaders(),
                                                             HttpVersion.HTTP_1_1, true, true);
                    out.add(last);
                } else {
                    FullHttpMessage full = newFullMessage(id, headers, ctx.alloc());
                    out.add(full);
                }
            } else {
                HttpMessage req = newMessage(id, headers);
                if (!HttpUtil.isContentLengthSet(req)) {
                    req.headers().add(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                }
                out.add(req);
            }
        } else if (frame instanceof Http2DataFrame) {
            Http2DataFrame dataFrame = (Http2DataFrame) frame;
            if (dataFrame.isEndStream()) {
                out.add(new DefaultLastHttpContent(dataFrame.content().retain(), validateHeaders));
            } else {
                out.add(new DefaultHttpContent(dataFrame.content().retain()));
            }
        }
    }

    private void encodeLastContent(LastHttpContent last, List<Object> out) {
        boolean needFiller = !(last instanceof FullHttpMessage) && last.trailingHeaders().isEmpty();
        if (last.content().isReadable() || needFiller) {
            out.add(new DefaultHttp2DataFrame(last.content().retain(), last.trailingHeaders().isEmpty()));
        }
        if (!last.trailingHeaders().isEmpty()) {
            Http2Headers headers = HttpConversionUtil.toHttp2Headers(last.trailingHeaders(), validateHeaders);
            out.add(new DefaultHttp2HeadersFrame(headers, true));
        }
    }

    /**
     * Encode from an {@link HttpObject} to an {@link Http2StreamFrame}. This method will
     * be called for each written message that can be handled by this encoder.
     *
     * NOTE: 100-Continue responses that are NOT {@link FullHttpResponse} will be rejected.
     *
     * @param ctx           the {@link ChannelHandlerContext} which this handler belongs to
     * @param obj           the {@link HttpObject} message to encode
     * @param out           the {@link List} into which the encoded msg should be added
     *                      needs to do some kind of aggregation
     * @throws Exception    is thrown if an error occurs
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, HttpObject obj, List<Object> out) throws Exception {
        // 100-continue is typically a FullHttpResponse, but the decoded
        // Http2HeadersFrame should not be marked as endStream=true
        if (obj instanceof HttpResponse) {
            final HttpResponse res = (HttpResponse) obj;
            if (res.status().equals(HttpResponseStatus.CONTINUE)) {
                if (res instanceof FullHttpResponse) {
                    final Http2Headers headers = toHttp2Headers(res);
                    out.add(new DefaultHttp2HeadersFrame(headers, false));
                    return;
                } else {
                    throw new EncoderException(
                            HttpResponseStatus.CONTINUE.toString() + " must be a FullHttpResponse");
                }
            }
        }

        if (obj instanceof HttpMessage) {
            Http2Headers headers = toHttp2Headers((HttpMessage) obj);
            boolean noMoreFrames = false;
            if (obj instanceof FullHttpMessage) {
                FullHttpMessage full = (FullHttpMessage) obj;
                noMoreFrames = !full.content().isReadable() && full.trailingHeaders().isEmpty();
            }

            out.add(new DefaultHttp2HeadersFrame(headers, noMoreFrames));
        }

        if (obj instanceof LastHttpContent) {
            LastHttpContent last = (LastHttpContent) obj;
            encodeLastContent(last, out);
        } else if (obj instanceof HttpContent) {
            HttpContent cont = (HttpContent) obj;
            out.add(new DefaultHttp2DataFrame(cont.content().retain(), false));
        }
    }

    private Http2Headers toHttp2Headers(final HttpMessage msg) {
        if (msg instanceof HttpRequest) {
            msg.headers().set(
                    HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(),
                    scheme.name());
        }

        return HttpConversionUtil.toHttp2Headers(msg, validateHeaders);
    }

    private HttpMessage newMessage(final int id,
                                   final Http2Headers headers) throws Http2Exception {
        return isServer ?
                HttpConversionUtil.toHttpRequest(id, headers, validateHeaders) :
                HttpConversionUtil.toHttpResponse(id, headers, validateHeaders);
    }

    private FullHttpMessage newFullMessage(final int id,
                                           final Http2Headers headers,
                                           final ByteBufAllocator alloc) throws Http2Exception {
        return isServer ?
                HttpConversionUtil.toFullHttpRequest(id, headers, alloc, validateHeaders) :
                HttpConversionUtil.toFullHttpResponse(id, headers, alloc, validateHeaders);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        super.handlerAdded(ctx);

        // this handler is typically used on an Http2StreamChannel. at this
        // stage, ssl handshake should've been established. checking for the
        // presence of SslHandler in the parent's channel pipeline to
        // determine the HTTP scheme should suffice, even for the case where
        // SniHandler is used.
        scheme = isSsl(ctx) ? HttpScheme.HTTPS : HttpScheme.HTTP;
    }

    protected boolean isSsl(final ChannelHandlerContext ctx) {
        final Channel ch = ctx.channel();
        final Channel connChannel = (ch instanceof Http2StreamChannel) ? ch.parent() : ch;
        return null != connChannel.pipeline().get(SslHandler.class);
    }
}
