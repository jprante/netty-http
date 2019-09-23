/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.reactivex.netty.protocol.http.sse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An object representing a server-sent-event following the <a href="http://www.w3.org/TR/eventsource/">SSE specifications</a>
 *
 * A server sent event is composed of the following:
 *
 * <ul>
 <li>Event id: This is the last event id seen on the stream this event was received. This can be null, if no id is received.</li>
 <li>Event type: The last seen event type seen on the stream this event was received. This can be null, if no type is received.</li>
 <li>Data: This is the actual event data.</li>
 </ul>
 *
 * <h2>Type</h2>
 *
 * A {@link ServerSentEvent} is of the type {@link Type#Data} unless it is explicitly passed on creation.
 *
 * <h2>Memory management</h2>
 *
 * This is an implementation of {@link ByteBufHolder} so it is required to be explicitly released by calling
 * {@link #release()} when this instance is no longer required.
 */
public class ServerSentEvent implements ByteBufHolder {

    private static final Logger logger = Logger.getLogger(ServerSentEvent.class.getName());

    private static Charset sseEncodingCharset;

    static {
        try {
            sseEncodingCharset = StandardCharsets.UTF_8;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "UTF-8 charset not available. Since SSE only contains UTF-8 data, we can not read SSE data.");
            sseEncodingCharset = null;
        }
    }

    public enum Type {
        Data,
        Id,
        EventType
    }

    private final Type type;
    /*This is required to make sure we allocate ByteBuf only inside an eventloop, else the ByteBuf pool will grow in the
    * owner thread*/
    private final String dataAsString;
    private final ByteBuf data;
    private final ByteBuf eventId;
    private final ByteBuf eventType;

    public ServerSentEvent(Type type, ByteBuf data) {
        this(type, null, null, data);
    }

    public ServerSentEvent(ByteBuf data) {
        this(Type.Data, data);
    }

    public ServerSentEvent(ByteBuf eventId, ByteBuf eventType, ByteBuf data) {
        this(Type.Data, eventId, eventType, data);
    }

    protected ServerSentEvent(Type type, ByteBuf eventId, ByteBuf eventType, ByteBuf data) {
        dataAsString = null;
        this.data = data;
        this.type = type;
        this.eventId = eventId;
        this.eventType = eventType;
    }

    private ServerSentEvent(String data) {
        dataAsString = data;
        this.data = null;
        type = Type.Data;
        eventId = null;
        eventType = null;
    }

    /**
     * The type of this event. For events which contain an event Id or event type along with data, the type is still
     * {@link Type#Data}. The type will be {@link Type#Id} or {@link Type#EventType} only if the event just contains the
     * event type or event id and no data.
     *
     * @return Type of this event.
     */
    public Type getType() {
        return type;
    }

    public boolean hasEventId() {
        return null != eventId;
    }

    public boolean hasEventType() {
        return null != eventType;
    }

    public ByteBuf getEventId() {
        return eventId;
    }

    public String getEventIdAsString() {
        return eventId.toString(getSseCharset());
    }

    public ByteBuf getEventType() {
        return eventType;
    }

    public String getEventTypeAsString() {
        return eventType.toString(getSseCharset());
    }

    public boolean hasDataAsString() {
        return null != dataAsString;
    }

    public String contentAsString() {
        return null != dataAsString ? dataAsString : data.toString(getSseCharset());
    }

    @Override
    public ByteBuf content() {
        return null != data ? data : Unpooled.buffer().writeBytes(dataAsString.getBytes(getSseCharset()));
    }

    @Override
    public ByteBufHolder copy() {
        if (hasDataAsString()) {
            return new ServerSentEvent(dataAsString);
        } else {
            return new ServerSentEvent(type, null != eventId? eventId.copy() : null,
                                       null != eventType? eventType.copy() : null, data.copy());
        }
    }

    @Override
    public ByteBufHolder duplicate() {
        if (hasDataAsString()) {
            return new ServerSentEvent(dataAsString);
        } else {
            return new ServerSentEvent(type, null != eventId ? eventId.duplicate() : null,
                                       null != eventType ? eventType.duplicate() : null, data.duplicate());
        }
    }

    @Override
    public ByteBufHolder retainedDuplicate() {
        return duplicate().retain();
    }

    @Override
    public ByteBufHolder replace(ByteBuf content) {
        return new ServerSentEvent(content);
    }

    @Override
    public int refCnt() {
        return hasDataAsString() ? 1 : data.refCnt(); // Ref count is consistent across data, eventId and eventType
    }

    @Override
    public ByteBufHolder retain() {
        if(hasEventId()) {
            eventId.retain();
        }
        if(hasEventType()) {
            eventType.retain();
        }
        if (!hasDataAsString()) {
            data.retain();
        }
        return this;
    }

    @Override
    public ByteBufHolder retain(int increment) {
        if(hasEventId()) {
            eventId.retain(increment);
        }
        if(hasEventType()) {
            eventType.retain(increment);
        }
        if (!hasDataAsString()) {
            data.retain(increment);
        }
        return this;
    }

    @Override
    public ByteBufHolder touch() {
        return touch(null);
    }

    @Override
    public ByteBufHolder touch(Object hint) {
        if (!hasDataAsString()) {
            data.touch(hint);
        }
        return this;
    }

    @Override
    public boolean release() {
        return data.release(1);
    }

    @Override
    public boolean release(int decrement) {
        if(hasEventId()) {
            eventId.release(decrement);
        }
        if(hasEventType()) {
            eventType.release(decrement);
        }
        return data.release(decrement);
    }

    /**
     * Creates a {@link ServerSentEvent} instance with an event id.
     *
     * @param eventId Id for the event.
     * @param data Data for the event.
     *
     * @return The {@link ServerSentEvent} instance.
     */
    public static ServerSentEvent withEventId(ByteBuf eventId, ByteBuf data) {
        return new ServerSentEvent(eventId, null, data);
    }

    /**
     * Creates a {@link ServerSentEvent} instance with an event type.
     *
     * @param eventType Type for the event.
     * @param data Data for the event.
     *
     * @return The {@link ServerSentEvent} instance.
     */
    public static ServerSentEvent withEventType(ByteBuf eventType, ByteBuf data) {
        return new ServerSentEvent(null, eventType, data);
    }

    /**
     * Creates a {@link ServerSentEvent} instance with an event id and type.
     *
     * @param eventType Type for the event.
     * @param eventId Id for the event.
     * @param data Data for the event.
     *
     * @return The {@link ServerSentEvent} instance.
     */
    public static ServerSentEvent withEventIdAndType(ByteBuf eventId, ByteBuf eventType, ByteBuf data) {
        return new ServerSentEvent(eventId, eventType, data);
    }

    /**
     * Creates a {@link ServerSentEvent} instance with data.
     *
     * @param data Data for the event.
     *
     * @return The {@link ServerSentEvent} instance.
     */
    public static ServerSentEvent withData(ByteBuf data) {
        return new ServerSentEvent(data);
    }

    /**
     * Creates a {@link ServerSentEvent} instance with data.
     *
     * @param data Data for the event.
     *
     * @return The {@link ServerSentEvent} instance.
     */
    public static ServerSentEvent withData(String data) {
        return new ServerSentEvent(data);
    }

    protected Charset getSseCharset() {
        return null == sseEncodingCharset ? Charset.forName("UTF-8") : sseEncodingCharset;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (hasEventId()) {
            sb.append("id: ");
            sb.append(getEventIdAsString());
            sb.append('\n');
        }

        if (hasEventType()) {
            sb.append("event: ");
            sb.append(getEventTypeAsString());
            sb.append('\n');
        }

        sb.append("data: ");
        sb.append(contentAsString());
        sb.append('\n');

        return sb.toString();
    }
}
