package org.xbib.netty.http.server.handler.http2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.CharSequenceMap;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.internal.ObjectUtil;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerConfig;
import org.xbib.netty.http.server.transport.ServerTransport;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A HTTP/2 event adapter for a server.
 *
 * This event adapter expects {@link Http2Settings} are sent from the server before the
 * {@link HttpRequest} is submitted by sending a header frame, and, if a body exists, a
 * data frame.
 */
@ChannelHandler.Sharable
public class Http2Handler extends Http2EventAdapter {

    private static final Logger logger = Logger.getLogger(Http2Handler.class.getName());

    private static final HttpVersion HTTP_2_0 = HttpVersion.valueOf("HTTP/2.0");

    private final Server server;

    private final ServerConfig serverConfig;

    private final ServerTransport serverTransport;

    private final Http2Connection connection;

    private final Http2Connection.PropertyKey messageKey;

    private final boolean validateHttpHeaders;

    /**
     * Constructor for {@link Http2Handler}.
     * @param server the server
     * @param connection the HTTP/2 connection
     * @param validateHeaders true if headers should be validated
     */
    public Http2Handler(Server server, Http2Connection connection, boolean validateHeaders) {
        this.server = server;
        this.serverConfig = server.getServerConfig();
        this.connection = connection;
        this.validateHttpHeaders = validateHeaders;
        this.messageKey = connection.newKey();
        this.serverTransport = server.newTransport(HTTP_2_0);
    }

    /**
     * Handles an inbound {@code SETTINGS} frame.
     * After frame is received, the request is sent.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param settings the settings received from the remote endpoint.
     */
    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "settings received " + settings);
        }
        try {
            serverTransport.settingsReceived(ctx, settings);
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
    }

    /**
     * Handles an inbound {@code HEADERS} frame.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     */
    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "headers received " + headers + " endOfStream " + endOfStream);
        }
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = beginHeader(ctx, stream, headers);
        endHeader(ctx, stream, msg, endOfStream);
    }

    /**
     * Handles an inbound {@code HEADERS} frame with priority information specified.
     * Only called if {@code END_HEADERS} encountered.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param headers the received headers.
     * @param streamDependency the stream on which this stream depends, or 0 if dependent on the
     *            connection.
     * @param weight the new weight for the stream.
     * @param exclusive whether or not the stream should be the exclusive dependent of its parent.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint
     *            for this stream.
     */
    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
                              short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "headers received (weighted) " + headers + " endOfStream " + endOfStream);
        }
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = beginHeader(ctx, stream, headers);
        if (streamDependency != Http2CodecUtil.CONNECTION_STREAM_ID) {
            msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(),
                    streamDependency);
        }
        msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), weight);
        endHeader(ctx, stream, msg, endOfStream);
    }

    /**
     * Handles an inbound {@code DATA} frame.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the subject stream for the frame.
     * @param data payload buffer for the frame. This buffer will be released by the codec.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     * @param endOfStream Indicates whether this is the last frame to be sent from the remote endpoint for this stream.
     * @return the number of bytes that have been processed by the application. The returned bytes are used by the
     * inbound flow controller to determine the appropriate time to expand the inbound flow control window (i.e. send
     * {@code WINDOW_UPDATE}). Returning a value equal to the length of {@code data} + {@code padding} will effectively
     * opt-out of application-level flow control for this frame. Returning a value less than the length of {@code data}
     * + {@code padding} will defer the returning of the processed bytes, which the application must later return via
     * {@link Http2LocalFlowController#consumeBytes(Http2Stream, int)}. The returned value must
     * be >= {@code 0} and <= {@code data.readableBytes()} + {@code padding}.
     */
    @Override
    public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream)
            throws Http2Exception {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "data received " + data);
        }
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg == null) {
            throw Http2Exception.connectionError(Http2Error.PROTOCOL_ERROR,
                    "data frame received for unknown stream id %d", streamId);
        }
        ByteBuf content = msg.content();
        final int dataReadableBytes = data.readableBytes();
        if (content.readableBytes() > serverConfig.getMaxContentLength() - dataReadableBytes) {
            throw Http2Exception.connectionError(Http2Error.INTERNAL_ERROR,
                    "content length exceeded maximum of %d for stream id %d",
                    serverConfig.getMaxContentLength(), streamId);
        }
        content.writeBytes(data, data.readerIndex(), dataReadableBytes);
        if (endOfStream) {
            fireChannelRead(ctx, msg, false, stream);
        }
        return dataReadableBytes + padding;
    }

    /**
     * Handles an inbound {@code RST_STREAM} frame. Deletes push stream id if present.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream that is terminating.
     * @param errorCode the error code identifying the type of failure.
     */
    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "rst stream received: error code = " + errorCode);
        }
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg != null) {
            removeMessage(stream, true);
        }
    }

    /**
     * Handles an inbound {@code PUSH_PROMISE} frame. Only called if {@code END_HEADERS} encountered.
     * <p>
     * Promised requests MUST be authoritative, cacheable, and safe.
     * See <a href="https://tools.ietf.org/html/draft-ietf-httpbis-http2-17#section-8.2">[RFC http2], Section 8.2</a>.
     * <p>
     * Only one of the following methods will be called for each {@code HEADERS} frame sequence.
     * One will be called when the {@code END_HEADERS} flag has been received.
     * <ul>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, boolean)}</li>
     * <li>{@link #onHeadersRead(ChannelHandlerContext, int, Http2Headers, int, short, boolean, int, boolean)}</li>
     * <li>{@link #onPushPromiseRead(ChannelHandlerContext, int, int, Http2Headers, int)}</li>
     * </ul>
     * <p>
     * To say it another way; the {@link Http2Headers} will contain all of the headers
     * for the current message exchange step (additional queuing is not necessary).
     *
     * @param ctx the context from the handler where the frame was read.
     * @param streamId the stream the frame was sent on.
     * @param promisedStreamId the ID of the promised stream.
     * @param headers the received headers.
     * @param padding additional bytes that should be added to obscure the true content size. Must be between 0 and
     *                256 (inclusive).
     */
    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "push promise received: streamId " + streamId +
                    " promised stream ID = " + promisedStreamId + " headers =" + headers);
        }
        throw new IllegalStateException("server is not allowd to receive push promise");
    }

    /**
     * Notifies the listener that the given stream has now been removed from the connection and
     * will no longer be returned via {@link Http2Connection#stream(int)}. The connection may
     * maintain inactive streams for some time before removing them.
     * <p>
     * If a {@link RuntimeException} is thrown it will be logged and <strong>not propagated</strong>.
     * Throwing from this method is not supported and is considered a programming error.
     */
    @Override
    public void onStreamRemoved(Http2Stream stream) {
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, () -> "stream removed " + stream);
        }
        removeMessage(stream, true);
    }


    /**
     * Get the {@link FullHttpMessage} associated with {@code stream}.
     * @param stream The stream to get the associated state from
     * @return The {@link FullHttpMessage} associated with {@code stream}.
     */
    private FullHttpMessage getMessage(Http2Stream stream) {
        return (FullHttpMessage) stream.getProperty(messageKey);
    }

    /**
     * Make {@code message} be the state associated with {@code stream}.
     * @param stream The stream which {@code message} is associated with.
     * @param message The message which contains the HTTP semantics.
     */
    private void putMessage(Http2Stream stream, FullHttpMessage message) {
        FullHttpMessage previous = stream.setProperty(messageKey, message);
        if (previous != message && previous != null) {
            previous.release();
        }
    }
    /**
     * The stream is out of scope for the HTTP message flow and will no longer be tracked.
     * @param stream The stream to remove associated state with
     * @param release {@code true} to call release on the value if it is present. {@code false} to not call release.
     */
    private void removeMessage(Http2Stream stream, boolean release) {
        FullHttpMessage msg = stream.removeProperty(messageKey);
        if (release && msg != null) {
            msg.release();
        }
    }

    private FullHttpMessage beginHeader(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers) throws Http2Exception {
        FullHttpMessage msg = getMessage(stream);
        if (msg == null) {
            msg = newMessage(stream, headers, validateHttpHeaders, ctx.alloc());
        } else {
            addHttp2ToHttpHeaders(stream.id(), headers, msg.headers(), msg.protocolVersion(),
                    true, msg instanceof HttpRequest);
        }
        return msg;
    }

    private void endHeader(ChannelHandlerContext ctx, Http2Stream stream, FullHttpMessage msg, boolean endOfStream) {
        if (endOfStream) {
            fireChannelRead(ctx, msg, getMessage(stream) != msg, stream);
        } else {
            putMessage(stream, msg);
        }
    }

    /**
     * Set final headers and fire a channel read event.
     *
     * @param ctx The context to fire the event on
     * @param msg The message to send
     * @param release {@code true} to call release on the value if it is present. {@code false} to not call release.
     * @param stream the stream of the message which is being fired
     */
    private void fireChannelRead(ChannelHandlerContext ctx, FullHttpMessage msg, boolean release,
                                 Http2Stream stream) {
        removeMessage(stream, release);
        HttpUtil.setContentLength(msg, msg.content().readableBytes());
        ctx.fireChannelRead(msg);
    }

    /**
     * Create a new {@link FullHttpMessage} based upon the current connection parameters.
     *
     * @param stream The stream to create a message for
     * @param headers The headers associated with {@code stream}
     * @param validateHttpHeaders
     * <ul>
     * <li>{@code true} to validate HTTP headers in the http-codec</li>
     * <li>{@code false} not to validate HTTP headers in the http-codec</li>
     * </ul>
     * @param alloc The {@link ByteBufAllocator} to use to generate the content of the message
     * @throws Http2Exception if message can not be created
     */
    private FullHttpMessage newMessage(Http2Stream stream, Http2Headers headers, boolean validateHttpHeaders,
                                       ByteBufAllocator alloc) throws Http2Exception {
        FullHttpMessage fullHttpMessage = toFullHttpRequest(stream.id(), headers, alloc, validateHttpHeaders);
        if (serverConfig.isDebug()) {
            logger.log(Level.FINE, headers.toString());
            logger.log(Level.FINE, fullHttpMessage::toString);
        }
        return fullHttpMessage;
    }

    /**
     * Create a new object to contain the request data
     *
     * @param streamId The stream associated with the request
     * @param http2Headers The initial set of HTTP/2 headers to create the request with
     * @param alloc The {@link ByteBufAllocator} to use to generate the content of the message
     * @param validateHttpHeaders <ul>
     *        <li>{@code true} to validate HTTP headers in the http-codec</li>
     *        <li>{@code false} not to validate HTTP headers in the http-codec</li>
     *        </ul>
     * @return A new request object which represents headers/data
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x.
     */
    public static FullHttpRequest toFullHttpRequest(int streamId, Http2Headers http2Headers,
                                                    ByteBufAllocator alloc,
                                                    boolean validateHttpHeaders)
            throws Http2Exception {
        final CharSequence method = ObjectUtil.checkNotNull(http2Headers.method(),"method header cannot be null");
        final CharSequence path = ObjectUtil.checkNotNull(http2Headers.path(),"path header cannot be null ");
        ByteBuf byteBuf = alloc.buffer();
        FullHttpRequest msg = new DefaultFullHttpRequest(HTTP_2_0, HttpMethod.valueOf(method.toString()),
                path.toString(), byteBuf, validateHttpHeaders);
        try {
            addHttp2ToHttpHeaders(streamId, http2Headers, msg.headers(), msg.protocolVersion(), false, true);
        } catch (Http2Exception e) {
            msg.release();
            throw e;
        } catch (Throwable t) {
            msg.release();
            throw Http2Exception.streamError(streamId, Http2Error.PROTOCOL_ERROR, t, "HTTP/2 full request conversion error");
        }
        return msg;
    }

    /**
     * Translate and add HTTP/2 headers to HTTP/1.x headers.
     *
     * @param streamId The stream associated with {@code sourceHeaders}.
     * @param inputHeaders The HTTP/2 headers to convert.
     * @param outputHeaders The object which will contain the resulting HTTP/1.x headers..
     * @param httpVersion What HTTP/1.x version {@code outputHeaders} should be treated as when doing the conversion.
     * @param isTrailer {@code true} if {@code outputHeaders} should be treated as trailing headers.
     * {@code false} otherwise.
     * @param isRequest {@code true} if the {@code outputHeaders} will be used in a request message.
     * {@code false} for response message.
     * @throws Http2Exception If not all HTTP/2 headers can be translated to HTTP/1.x.
     */
    public static void addHttp2ToHttpHeaders(int streamId, Http2Headers inputHeaders,
                                             HttpHeaders outputHeaders,
                                             HttpVersion httpVersion,
                                             boolean isTrailer,
                                             boolean isRequest) throws Http2Exception {

        final CharSequenceMap<AsciiString> translations = isRequest ? REQUEST_HEADER_TRANSLATIONS : RESPONSE_HEADER_TRANSLATIONS;
        try {
            for (Map.Entry<CharSequence, CharSequence> entry : inputHeaders) {
                final CharSequence name = entry.getKey();
                final CharSequence value = entry.getValue();
                AsciiString translatedName = translations.get(name);
                if (translatedName != null) {
                    outputHeaders.add(translatedName, AsciiString.of(value));
                } else if (!Http2Headers.PseudoHeaderName.isPseudoHeader(name)) {
                    // https://tools.ietf.org/html/rfc7540#section-8.1.2.3
                    // All headers that start with ':' are only valid in HTTP/2 context
                    if (name.length() == 0 || name.charAt(0) == ':') {
                        throw Http2Exception.streamError(streamId, Http2Error.PROTOCOL_ERROR,
                                "Invalid HTTP/2 header '%s' encountered in translation to HTTP/1.x", name);
                    }
                    if (HttpHeaderNames.COOKIE.equals(name)) {
                        // combine the cookie values into 1 header entry.
                        // https://tools.ietf.org/html/rfc7540#section-8.1.2.5
                        String existingCookie = outputHeaders.get(HttpHeaderNames.COOKIE);
                        outputHeaders.set(HttpHeaderNames.COOKIE,
                                (existingCookie != null) ? (existingCookie + "; " + value) : value);
                    } else {
                        outputHeaders.add(name, value);
                    }
                }
            }
        } catch (Http2Exception ex) {
            throw ex;
        } catch (Throwable t) {
            throw Http2Exception.streamError(streamId, Http2Error.PROTOCOL_ERROR, t, "HTTP/2 headers conversion error");
        }
        outputHeaders.remove(HttpHeaderNames.TRANSFER_ENCODING);
        outputHeaders.remove(HttpHeaderNames.TRAILER);
        if (!isTrailer) {
            outputHeaders.setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId);
            HttpUtil.setKeepAlive(outputHeaders, httpVersion, true);
        }
    }

    /**
     * Translations from HTTP/2 header name to the HTTP/1.x equivalent.
     */
    private static final CharSequenceMap<AsciiString>
            REQUEST_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
    private static final CharSequenceMap<AsciiString>
            RESPONSE_HEADER_TRANSLATIONS = new CharSequenceMap<AsciiString>();
    static {
        RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.AUTHORITY.value(),
                HttpHeaderNames.HOST);
        RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.SCHEME.value(),
                HttpConversionUtil.ExtensionHeaderNames.SCHEME.text());
        REQUEST_HEADER_TRANSLATIONS.add(RESPONSE_HEADER_TRANSLATIONS);
        RESPONSE_HEADER_TRANSLATIONS.add(Http2Headers.PseudoHeaderName.PATH.value(),
                HttpConversionUtil.ExtensionHeaderNames.PATH.text());
    }
}
