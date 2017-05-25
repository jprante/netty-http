/*
 * Copyright 2017 Jörg Prante
 *
 * Jörg Prante licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.xbib.netty.http.client.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2CodecUtil;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.handler.codec.http2.HttpConversionUtil;
import org.xbib.netty.http.client.HttpClientChannelContextDefaults;
import org.xbib.netty.http.client.HttpRequestContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A HTTP/2 event adapter for a client.
 * This event adapter expects {@link Http2Settings} are sent from the server before the
 * {@link HttpRequest} is submitted by sending a header frame, and, if a body exists, a
 * data frame.
 * The push promises of a server response are acknowledged and the headers of a push promise
 * are stored in the {@link HttpRequestContext} for being received later.
 */
public class Http2EventHandler extends Http2EventAdapter {

    private static final Logger logger = Logger.getLogger(Http2EventHandler.class.getName());

    private final Http2Connection connection;

    private final Http2Connection.PropertyKey messageKey;

    private final int maxContentLength;

    private final boolean validateHttpHeaders;

    /**
     * Constructor for {@link Http2EventHandler}.
     * @param connection the HTTP/2 connection
     * @param maxContentLength the maximum content length
     * @param validateHeaders true if headers should be validated
     */
    public Http2EventHandler(Http2Connection connection, int maxContentLength, boolean validateHeaders) {
        this.connection = connection;
        this.maxContentLength = maxContentLength;
        this.validateHttpHeaders = validateHeaders;
        this.messageKey = connection.newKey();
    }

    /**
     * Handles an inbound {@code SETTINGS} frame.
     * After frame is received, the request is sent.
     *
     * @param ctx the context from the handler where the frame was read.
     * @param settings the settings received from the remote endpoint.
     */
    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings)
            throws Http2Exception {
        logger.log(Level.FINEST, () -> "settings received " + settings);
        Channel channel = ctx.channel();
        final HttpRequestContext httpRequestContext =
                channel.attr(HttpClientChannelContextDefaults.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        final HttpRequest httpRequest = httpRequestContext.getHttpRequest();
        ChannelPromise channelPromise = channel.newPromise();
        Http2Headers headers = toHttp2Headers(httpRequestContext);
        logger.log(Level.FINEST, () -> "write request " + httpRequest + " headers = " + headers);
        boolean hasBody = httpRequestContext.getHttpRequest() instanceof FullHttpRequest;
        Http2ConnectionHandler handler = ctx.pipeline().get(Http2ConnectionHandler.class);
        Integer streamId = httpRequestContext.getStreamId().get();
        ChannelFuture channelFuture = handler.encoder().writeHeaders(ctx, streamId,
                headers, 0, !hasBody, channelPromise);
        httpRequestContext.putStreamID(streamId, channelFuture, channelPromise);
        if (hasBody) {
            FullHttpRequest fullHttpRequest = (FullHttpRequest) httpRequestContext.getHttpRequest();
            ChannelPromise contentChannelPromise = channel.newPromise();
            streamId = httpRequestContext.getStreamId().get();
            ChannelFuture contentChannelFuture = handler.encoder().writeData(ctx, streamId,
                    fullHttpRequest.content(), 0, true, contentChannelPromise);
            httpRequestContext.putStreamID(streamId, contentChannelFuture, contentChannelPromise);
            channel.flush();
        }
        httpRequestContext.getSettingsPromise().setSuccess();
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
        logger.log(Level.FINEST, () -> "headers received " + headers);
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = beginHeader(ctx, stream, headers, true, true);
        if (msg != null) {
            endHeader(ctx, stream, msg, endOfStream);
        }
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
        logger.log(Level.FINEST, () -> "headers received " + headers);
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = beginHeader(ctx, stream, headers, true, true);
        if (msg != null) {
            if (streamDependency != Http2CodecUtil.CONNECTION_STREAM_ID) {
                msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(),
                        streamDependency);
            }
            msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(), weight);
            endHeader(ctx, stream, msg, endOfStream);
        }
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
        logger.log(Level.FINEST, () -> "data received " + data);
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg == null) {
            throw Http2Exception.connectionError(Http2Error.PROTOCOL_ERROR,
                    "data frame received for unknown stream id %d", streamId);
        }
        ByteBuf content = msg.content();
        final int dataReadableBytes = data.readableBytes();
        if (content.readableBytes() > maxContentLength - dataReadableBytes) {
            throw Http2Exception.connectionError(Http2Error.INTERNAL_ERROR,
                    "content length exceeded maximum of %d for stream id %d", maxContentLength, streamId);
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
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        logger.log(Level.FINEST, () -> "rst stream received: error code = " + errorCode);
        Http2Stream stream = connection.stream(streamId);
        FullHttpMessage msg = getMessage(stream);
        if (msg != null) {
            removeMessage(stream, true);
        }
        final HttpRequestContext httpRequestContext =
                ctx.channel().attr(HttpClientChannelContextDefaults.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        httpRequestContext.getPushMap().remove(streamId);
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
                                  Http2Headers headers, int padding) throws Http2Exception {
        logger.log(Level.FINEST, () -> "push promise received: streamId " + streamId +
                " promised stream ID = " + promisedStreamId + " headers =" + headers);
        Http2Stream promisedStream = connection.stream(promisedStreamId);
        FullHttpMessage msg = beginHeader(ctx, promisedStream, headers, false, false);
        if (msg != null) {
            msg.headers().setInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_PROMISE_ID.text(), streamId);
            msg.headers().setShort(HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(),
                    Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT);
            endHeader(ctx, promisedStream, msg, false);
        }
        Channel channel = ctx.channel();
        final HttpRequestContext httpRequestContext =
                channel.attr(HttpClientChannelContextDefaults.REQUEST_CONTEXT_ATTRIBUTE_KEY).get();
        httpRequestContext.receiveStreamID(promisedStreamId, headers, channel.newPromise());
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
        logger.log(Level.FINEST, () -> "stream removed " + stream);
        removeMessage(stream, true);
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
        if (headers.status() != null) {
            return HttpConversionUtil.toHttpResponse(stream.id(), headers, alloc, validateHttpHeaders);
        } else {
            return null;
        }
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

    private FullHttpMessage beginHeader(ChannelHandlerContext ctx, Http2Stream stream, Http2Headers headers,
                                                  boolean allowAppend, boolean appendToTrailer) throws Http2Exception {
        FullHttpMessage msg = getMessage(stream);
        if (msg == null) {
            msg = newMessage(stream, headers, validateHttpHeaders, ctx.alloc());
        } else {
            if (allowAppend) {
                HttpConversionUtil.addHttp2ToHttpHeaders(stream.id(), headers, msg, appendToTrailer);
            } else {
                throw new Http2Exception(Http2Error.PROTOCOL_ERROR, "stream already exists");
            }
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

    private static Http2Headers toHttp2Headers(HttpRequestContext httpRequestContext) {
        HttpRequest httpRequest = httpRequestContext.getHttpRequest();
        Http2Headers headers = new DefaultHttp2Headers()
                .method(httpRequest.method().asciiName())
                .path(httpRequest.uri())
                .scheme(httpRequestContext.getURI().getScheme())
                .authority(httpRequestContext.getURI().getHost());
        HttpConversionUtil.toHttp2Headers(httpRequest.headers(), headers);
        return headers;
    }
}
