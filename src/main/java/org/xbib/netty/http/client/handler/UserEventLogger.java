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

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceWrittenEvent;
import io.netty.handler.ssl.SslCloseCompletionEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Netty handler that logs user events and find expetced ones.
 */
@ChannelHandler.Sharable
class UserEventLogger extends ChannelInboundHandlerAdapter {

    private static final Logger logger = Logger.getLogger(UserEventLogger.class.getName());

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        logger.log(Level.FINE, () -> "got user event " + evt);
        if (evt instanceof Http2ConnectionPrefaceWrittenEvent ||
                evt instanceof SslCloseCompletionEvent ||
                evt instanceof ChannelInputShutdownReadComplete) {
            // log expected events
            logger.log(Level.FINE, () -> "user event is expected: " + evt);
            return;
        }
        super.userEventTriggered(ctx, evt);
    }
}
