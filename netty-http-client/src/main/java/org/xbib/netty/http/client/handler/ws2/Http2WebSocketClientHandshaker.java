package org.xbib.netty.http.client.handler.ws2;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.websocketx.WebSocketDecoderConfig;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2LocalFlowController;
import io.netty.util.AsciiString;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import org.xbib.netty.http.common.ws.Http2WebSocket;
import org.xbib.netty.http.common.ws.Http2WebSocketChannel;
import org.xbib.netty.http.common.ws.Http2WebSocketChannelHandler;
import org.xbib.netty.http.common.ws.Http2WebSocketEvent;
import org.xbib.netty.http.common.ws.Http2WebSocketExtensions;
import org.xbib.netty.http.common.ws.Http2WebSocketMessages;
import org.xbib.netty.http.common.ws.Http2WebSocketProtocol;
import org.xbib.netty.http.common.ws.Preconditions;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Establishes websocket-over-http2 on provided connection channel
 */
public final class Http2WebSocketClientHandshaker {

    private static final Logger logger = Logger.getLogger(Http2WebSocketClientHandshaker.class.getName());

    private static final int ESTIMATED_DEFERRED_HANDSHAKES = 4;

    private static final AtomicIntegerFieldUpdater<Http2WebSocketClientHandshaker> WEBSOCKET_CHANNEL_SERIAL =
            AtomicIntegerFieldUpdater.newUpdater(Http2WebSocketClientHandshaker.class, "webSocketChannelSerial");

    private static final Http2Headers EMPTY_HEADERS = new DefaultHttp2Headers(false);

    private final Http2Connection.Endpoint<Http2LocalFlowController> streamIdFactory;

    private final WebSocketDecoderConfig webSocketDecoderConfig;

    private final Http2WebSocketChannelHandler.WebSocketsParent webSocketsParent;

    private final short streamWeight;

    private final CharSequence scheme;

    private final PerMessageDeflateClientExtensionHandshaker compressionHandshaker;

    private final boolean isEncoderMaskPayload;

    private final long timeoutMillis;

    private Queue<Handshake> deferred;

    private Boolean supportsWebSocket;

    private volatile int webSocketChannelSerial;

    private CharSequence compressionExtensionHeader;

    Http2WebSocketClientHandshaker(Http2WebSocketChannelHandler.WebSocketsParent webSocketsParent,
            Http2Connection.Endpoint<Http2LocalFlowController> streamIdFactory,
            WebSocketDecoderConfig webSocketDecoderConfig,
            boolean isEncoderMaskPayload,
            short streamWeight,
            CharSequence scheme,
            long handshakeTimeoutMillis, PerMessageDeflateClientExtensionHandshaker compressionHandshaker) {
        this.webSocketsParent = webSocketsParent;
        this.streamIdFactory = streamIdFactory;
        this.webSocketDecoderConfig = webSocketDecoderConfig;
        this.isEncoderMaskPayload = isEncoderMaskPayload;
        this.timeoutMillis = handshakeTimeoutMillis;
        this.streamWeight = streamWeight;
        this.scheme = scheme;
        this.compressionHandshaker = compressionHandshaker;
    }

    /**
     * Creates new {@link Http2WebSocketClientHandshaker} for given connection channel
     *
     * @param channel connection channel. Pipeline must contain {@link Http2WebSocketClientHandler}
     *     and netty http2 codec (e.g. Http2ConnectionHandler or Http2FrameCodec)
     * @return new {@link Http2WebSocketClientHandshaker} instance
     */
    public static Http2WebSocketClientHandshaker create(Channel channel) {
        Objects.requireNonNull(channel, "channel");
        return Preconditions.requireHandler(channel, Http2WebSocketClientHandler.class).handShaker();
    }

    /**
     * Starts websocket-over-http2 handshake using given path
     *
     * @param path websocket path, must be non-empty
     * @param webSocketHandler http1 websocket handler added to pipeline of subchannel created for
     *     successfully handshaked http2 websocket
     * @return ChannelFuture with result of handshake. Its channel accepts http1 WebSocketFrames as
     *     soon as this method returns.
     */
    public ChannelFuture handshake(String path, ChannelHandler webSocketHandler) {
        return handshake(path, "", EMPTY_HEADERS, webSocketHandler);
    }

    /**
     * Starts websocket-over-http2 handshake using given path and request headers
     *
     * @param path websocket path, must be non-empty
     * @param requestHeaders request headers, must be non-null
     * @param webSocketHandler http1 websocket handler added to pipeline of subchannel created for
     *     successfully handshaked http2 websocket
     * @return ChannelFuture with result of handshake. Its channel accepts http1 WebSocketFrames as
     *     soon as this method returns.
     */
    public ChannelFuture handshake(
            String path, Http2Headers requestHeaders, ChannelHandler webSocketHandler) {
        return handshake(path, "", requestHeaders, webSocketHandler);
    }

    /**
     * Starts websocket-over-http2 handshake using given path and subprotocol
     *
     * @param path websocket path, must be non-empty
     * @param subprotocol websocket subprotocol, must be non-null
     * @param webSocketHandler http1 websocket handler added to pipeline of subchannel created for
     *     successfully handshaked http2 websocket
     * @return ChannelFuture with result of handshake. Its channel accepts http1 WebSocketFrames as
     *     soon as this method returns.
     */
    public ChannelFuture handshake(String path, String subprotocol, ChannelHandler webSocketHandler) {
        return handshake(path, subprotocol, EMPTY_HEADERS, webSocketHandler);
    }

    /**
     * Starts websocket-over-http2 handshake using given path, subprotocol and request headers
     *
     * @param path websocket path, must be non-empty
     * @param subprotocol websocket subprotocol, must be non-null
     * @param requestHeaders request headers, must be non-null
     * @param webSocketHandler http1 websocket handler added to pipeline of subchannel created for
     *     successfully handshaked http2 websocket
     * @return ChannelFuture with result of handshake. Its channel accepts http1 WebSocketFrames as
     *     soon as this method returns.
     */
    public ChannelFuture handshake(String path, String subprotocol,
            Http2Headers requestHeaders, ChannelHandler webSocketHandler) {
        Preconditions.requireNonEmpty(path, "path");
        Preconditions.requireNonNull(subprotocol, "subprotocol");
        Preconditions.requireNonNull(requestHeaders, "requestHeaders");
        Preconditions.requireNonNull(webSocketHandler, "webSocketHandler");
        long startNanos = System.nanoTime();
        ChannelHandlerContext ctx = webSocketsParent.context();
        if (!ctx.channel().isOpen()) {
            return ctx.newFailedFuture(new ClosedChannelException());
        }
        int serial = WEBSOCKET_CHANNEL_SERIAL.getAndIncrement(this);
        Http2WebSocketChannel webSocketChannel = new Http2WebSocketChannel(webSocketsParent, serial, path,
                subprotocol, webSocketDecoderConfig, isEncoderMaskPayload, webSocketHandler).initialize();
        Handshake handshake = new Handshake(webSocketChannel, requestHeaders, timeoutMillis, startNanos);
        handshake.future().addListener(future -> {
            Throwable cause = future.cause();
            if (cause != null && !(cause instanceof WebSocketHandshakeException)) {
                Http2WebSocketEvent.fireHandshakeError(webSocketChannel, null, System.nanoTime(), cause);
            }
        });
        EventLoop el = ctx.channel().eventLoop();
        if (el.inEventLoop()) {
            handshakeOrDefer(handshake, el);
        } else {
            el.execute(() -> handshakeOrDefer(handshake, el));
        }
        return webSocketChannel.handshakePromise();
    }

    void handshake(Http2WebSocket webSocket, Http2Headers responseHeaders, boolean endOfStream) {
        if (webSocket == Http2WebSocket.CLOSED) {
            return;
        }
        Http2WebSocketChannel webSocketChannel = (Http2WebSocketChannel) webSocket;
        ChannelPromise handshakePromise = webSocketChannel.handshakePromise();
        if (handshakePromise.isDone()) {
            return;
        }
        String errorMessage = null;
        WebSocketClientExtension compressionExtension = null;
        String status = responseHeaders.status().toString();
        switch (status) {
            case "200":
                if (endOfStream) {
                    errorMessage = Http2WebSocketMessages.HANDSHAKE_UNEXPECTED_RESULT;
                } else {
                    String clientSubprotocol = webSocketChannel.subprotocol();
                    CharSequence serverSubprotocol =
                            responseHeaders.get(Http2WebSocketProtocol.HEADER_WEBSOCKET_SUBPROTOCOL_NAME);
                    if (!isEqual(clientSubprotocol, serverSubprotocol)) {
                        errorMessage =
                                Http2WebSocketMessages.HANDSHAKE_UNEXPECTED_SUBPROTOCOL + clientSubprotocol;
                    }
                    if (errorMessage == null) {
                        PerMessageDeflateClientExtensionHandshaker handshaker = compressionHandshaker;
                        if (handshaker != null) {
                            CharSequence extensionsHeader = responseHeaders.get(Http2WebSocketProtocol.HEADER_WEBSOCKET_EXTENSIONS_NAME);
                            WebSocketExtensionData compression = Http2WebSocketExtensions.decode(extensionsHeader);
                            if (compression != null) {
                                compressionExtension = handshaker.handshakeExtension(compression);
                            }
                        }
                    }
                }
                break;
            case "400":
                CharSequence webSocketVersion =
                        responseHeaders.get(Http2WebSocketProtocol.HEADER_WEBSOCKET_VERSION_NAME);
                errorMessage = webSocketVersion != null
                                ? Http2WebSocketMessages.HANDSHAKE_UNSUPPORTED_VERSION + webSocketVersion
                                : Http2WebSocketMessages.HANDSHAKE_BAD_REQUEST;
                break;
            case "404":
                errorMessage = Http2WebSocketMessages.HANDSHAKE_PATH_NOT_FOUND
                                + webSocketChannel.path()
                                + Http2WebSocketMessages.HANDSHAKE_PATH_NOT_FOUND_SUBPROTOCOLS
                                + webSocketChannel.subprotocol();
                break;
            default:
                errorMessage = Http2WebSocketMessages.HANDSHAKE_GENERIC_ERROR + status;
        }
        if (errorMessage != null) {
            Exception cause = new WebSocketHandshakeException(errorMessage);
            if (handshakePromise.tryFailure(cause)) {
                Http2WebSocketEvent.fireHandshakeError(webSocketChannel, responseHeaders, System.nanoTime(), cause);
            }
            return;
        }
        if (compressionExtension != null) {
            webSocketChannel.compression(compressionExtension.newExtensionEncoder(), compressionExtension.newExtensionDecoder());
        }
        if (handshakePromise.trySuccess()) {
            Http2WebSocketEvent.fireHandshakeSuccess(webSocketChannel, responseHeaders, System.nanoTime());
        }
    }

    void reject(int streamId, Http2WebSocket webSocket, Http2Headers headers, boolean endOfStream) {
        Http2WebSocketEvent.fireHandshakeValidationStartAndError(webSocketsParent.context().channel(),
                streamId, headers.set(AsciiString.of("x-websocket-endofstream"), AsciiString.of(endOfStream ? "true" : "false")));
        if (webSocket == Http2WebSocket.CLOSED) {
            return;
        }
        Http2WebSocketChannel webSocketChannel = (Http2WebSocketChannel) webSocket;
        ChannelPromise handshakePromise = webSocketChannel.handshakePromise();
        if (handshakePromise.isDone()) {
            return;
        }
        Exception cause = new WebSocketHandshakeException(Http2WebSocketMessages.HANDSHAKE_INVALID_RESPONSE_HEADERS);
        if (handshakePromise.tryFailure(cause)) {
            Http2WebSocketEvent.fireHandshakeError(webSocketChannel, headers, System.nanoTime(), cause);
        }
    }

    void onSupportsWebSocket(boolean supportsWebSocket) {
        if (!supportsWebSocket) {
            logger.log(Level.SEVERE, Http2WebSocketMessages.HANDSHAKE_UNSUPPORTED_BOOTSTRAP);
        }
        this.supportsWebSocket = supportsWebSocket;
        handshakeDeferred(supportsWebSocket);
    }

    private void handshakeOrDefer(Handshake handshake, EventLoop eventLoop) {
        if (handshake.isDone()) {
            return;
        }
        Http2WebSocketChannel webSocketChannel = handshake.webSocketChannel();
        Http2Headers requestHeaders = handshake.requestHeaders();
        long startNanos = handshake.startNanos();
       ChannelFuture registered = eventLoop.register(webSocketChannel);
        if (!registered.isSuccess()) {
            Throwable cause = registered.cause();
            Exception e = new WebSocketHandshakeException("websocket handshake channel registration error", cause);
            Http2WebSocketEvent.fireHandshakeStartAndError(webSocketChannel.parent(),
                    webSocketChannel.serial(), webSocketChannel.path(), webSocketChannel.subprotocol(),
                    requestHeaders, startNanos, System.nanoTime(), e);
            handshake.complete(e);
            return;
        }
        Http2WebSocketEvent.fireHandshakeStart(webSocketChannel, requestHeaders, startNanos);
        Boolean supports = supportsWebSocket;
        if (supports == null) {
            Queue<Handshake> d = deferred;
            if (d == null) {
                d = deferred = new ArrayDeque<>(ESTIMATED_DEFERRED_HANDSHAKES);
            }
            handshake.startTimeout();
            d.add(handshake);
            return;
        }
        if (supports) {
            handshake.startTimeout();
        }
        handshakeImmediate(handshake, supports);
    }

    private void handshakeDeferred(boolean supportsWebSocket) {
        Queue<Handshake> d = deferred;
        if (d == null) {
            return;
        }
        deferred = null;
        Handshake handshake = d.poll();
        while (handshake != null) {
            handshakeImmediate(handshake, supportsWebSocket);
            handshake = d.poll();
        }
    }

    private void handshakeImmediate(Handshake handshake, boolean supportsWebSocket) {
        Http2WebSocketChannel webSocketChannel = handshake.webSocketChannel();
        Http2Headers customHeaders = handshake.requestHeaders();
        if (handshake.isDone()) {
            return;
        }
        if (!supportsWebSocket) {
            WebSocketHandshakeException e = new WebSocketHandshakeException(Http2WebSocketMessages.HANDSHAKE_UNSUPPORTED_BOOTSTRAP);
            Http2WebSocketEvent.fireHandshakeError(webSocketChannel, null, System.nanoTime(), e);
            handshake.complete(e);
            return;
        }
        int streamId = streamIdFactory.incrementAndGetNextStreamId();
        webSocketsParent.register(streamId, webSocketChannel.setStreamId(streamId));
        String authority = authority();
        String path = webSocketChannel.path();
        Http2Headers headers = Http2WebSocketProtocol.extendedConnect(new DefaultHttp2Headers()
            .scheme(scheme)
            .authority(authority)
            .path(path)
            .set(Http2WebSocketProtocol.HEADER_WEBSOCKET_VERSION_NAME,
                    Http2WebSocketProtocol.HEADER_WEBSOCKET_VERSION_VALUE));
        PerMessageDeflateClientExtensionHandshaker handshaker = compressionHandshaker;
        if (handshaker != null) {
            headers.set(Http2WebSocketProtocol.HEADER_WEBSOCKET_EXTENSIONS_NAME,
                    compressionExtensionHeader(handshaker));
        }
        String subprotocol = webSocketChannel.subprotocol();
        if (!subprotocol.isEmpty()) {
            headers.set(Http2WebSocketProtocol.HEADER_WEBSOCKET_SUBPROTOCOL_NAME, subprotocol);
        }
        if (!customHeaders.isEmpty()) {
            headers.setAll(customHeaders);
        }
        short pendingStreamWeight = webSocketChannel.pendingStreamWeight();
        short weight = pendingStreamWeight > 0 ? pendingStreamWeight : streamWeight;
        webSocketsParent.writeHeaders(webSocketChannel.streamId(), headers, false, weight)
            .addListener(future -> {
                if (!future.isSuccess()) {
                    handshake.complete(future.cause());
                    return;
                }
                webSocketChannel.setStreamWeightAttribute(weight);
            });
    }

    private String authority() {
        return ((InetSocketAddress) webSocketsParent.context().channel().remoteAddress()).getHostString();
    }

    private CharSequence compressionExtensionHeader(PerMessageDeflateClientExtensionHandshaker handshaker) {
        CharSequence header = compressionExtensionHeader;
        if (header == null) {
            header = compressionExtensionHeader = AsciiString.of(Http2WebSocketExtensions.encode(handshaker.newRequestData()));
        }
        return header;
    }

    private static boolean isEqual(String str, CharSequence seq) {
        if ((seq == null || seq.length() == 0) && str.isEmpty()) {
            return true;
        }
        if (seq == null) {
            return false;
        }
        return str.contentEquals(seq);
    }

    static class Handshake {
        private final Future<Void> channelClose;
        private final ChannelPromise handshake;
        private final long timeoutMillis;
        private boolean done;
        private ScheduledFuture<?> timeoutFuture;
        private Future<?> handshakeCompleteFuture;
        private GenericFutureListener<ChannelFuture> channelCloseListener;
        private final Http2WebSocketChannel webSocketChannel;
        private final Http2Headers requestHeaders;
        private final long handshakeStartNanos;

        public Handshake(Http2WebSocketChannel webSocketChannel, Http2Headers requestHeaders,
                         long timeoutMillis, long handshakeStartNanos) {
            this.channelClose = webSocketChannel.closeFuture();
            this.handshake = webSocketChannel.handshakePromise();
            this.timeoutMillis = timeoutMillis;
            this.webSocketChannel = webSocketChannel;
            this.requestHeaders = requestHeaders;
            this.handshakeStartNanos = handshakeStartNanos;
        }

        public void startTimeout() {
            ChannelPromise h = handshake;
            Channel channel = h.channel();
            if (done) {
                return;
            }
            GenericFutureListener<ChannelFuture> l = channelCloseListener = future -> onConnectionClose();
            channelClose.addListener(l);
            if (done) {
                return;
            }
            handshakeCompleteFuture = h.addListener(future -> onHandshakeComplete(future.cause()));
            if (done) {
                return;
            }
            timeoutFuture = channel.eventLoop().schedule(this::onTimeout, timeoutMillis, TimeUnit.MILLISECONDS);
        }

        public void complete(Throwable e) {
            onHandshakeComplete(e);
        }

        public boolean isDone() {
            return done;
        }

        public ChannelFuture future() {
            return handshake;
        }

        public Http2WebSocketChannel webSocketChannel() {
            return webSocketChannel;
        }

        public Http2Headers requestHeaders() {
            return requestHeaders;
        }

        public long startNanos() {
            return handshakeStartNanos;
        }
        private void onConnectionClose() {
            if (!done) {
                handshake.tryFailure(new ClosedChannelException());
                done();
            }
        }

        private void onHandshakeComplete(Throwable cause) {
            if (!done) {
                if (cause != null) {
                    handshake.tryFailure(cause);
                } else {
                    handshake.trySuccess();
                }
                done();
            }
        }

        private void onTimeout() {
            if (!done) {
                handshake.tryFailure(new TimeoutException());
                done();
            }
        }

        private void done() {
            done = true;
            GenericFutureListener<ChannelFuture> closeListener = channelCloseListener;
            if (closeListener != null) {
                channelClose.removeListener(closeListener);
            }
            cancel(handshakeCompleteFuture);
            cancel(timeoutFuture);
        }

        private void cancel(Future<?> future) {
            if (future != null) {
                future.cancel(true);
            }
        }
    }
}
