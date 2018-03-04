package org.xbib.netty.http.client.handler.http2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DelegatingDecompressorFrameListener;
import io.netty.handler.codec.http2.Http2Connection;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2FrameListener;
import io.netty.handler.codec.http2.Http2Headers;
import org.xbib.netty.http.client.transport.Transport;

public class Http2PushPromiseHandler extends DelegatingDecompressorFrameListener {

    public Http2PushPromiseHandler(Http2Connection connection, Http2FrameListener listener) {
        super(connection, listener);
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) throws Http2Exception {
        super.onPushPromiseRead(ctx, streamId, promisedStreamId, headers, padding);
        Transport transport = ctx.channel().attr(Transport.TRANSPORT_ATTRIBUTE_KEY).get();
        transport.pushPromiseReceived(streamId, promisedStreamId, headers);
    }
}
