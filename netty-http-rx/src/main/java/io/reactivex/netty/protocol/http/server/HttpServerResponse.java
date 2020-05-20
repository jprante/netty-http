/*
 * Copyright 2016 Netflix, Inc.
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
package io.reactivex.netty.protocol.http.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.cookie.Cookie;
import io.reactivex.netty.channel.AllocatingTransformer;
import io.reactivex.netty.channel.Connection;
import io.reactivex.netty.protocol.http.sse.ServerSentEvent;
import io.reactivex.netty.protocol.http.ws.server.WebSocketHandler;
import io.reactivex.netty.protocol.http.ws.server.WebSocketHandshaker;
import rx.Observable;
import rx.annotations.Experimental;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * An HTTP server response.
 *
 * <h2>Thread safety</h2>
 *
 * This object is <b>not</b> thread safe and should not be accessed from multiple threads.
 *
 * @param <C> The type of objects written as the content of the response.
 */
public abstract class HttpServerResponse<C> extends ResponseContentWriter<C> {

    protected HttpServerResponse(OnSubscribe<Void> f) {
        super(f);
    }

    /**
     * Returns the status of this response. If the status is not explicitly set, the default value is
     * {@link HttpResponseStatus#OK}
     *
     * @return The status of this response.
     */
    public abstract HttpResponseStatus getStatus();

    /**
     * Checks if there is a header with the passed name in this response.
     *
     * @param name Name of the header.
     *
     * @return {@code true} if there is a header with the passed name in this response.
     */
    public abstract boolean containsHeader(CharSequence name);

    /**
     * Checks if there is a header with the passed name and value in this response.
     *
     * @param name Name of the header.
     * @param value Value of the header.
     * @param ignoreCaseValue {@code true} then the value comparision is done ignoring case.
     *
     * @return {@code true} if there is a header with the passed name and value in this response.
     */
    public abstract boolean containsHeader(CharSequence name, CharSequence value, boolean ignoreCaseValue);

    /**
     * Returns the value of a header with the specified name.  If there are more than one values for the specified name,
     * the first value is returned.
     *
     * @param name The name of the header to search
     * @return The first header value or {@code null} if there is no such header
     */
    public abstract String getHeader(CharSequence name);

    /**
     * Returns the value of a header with the specified name.  If there are more than one values for the specified name,
     * the first value is returned.
     *
     * @param name The name of the header to search
     * @param defaultValue Default if the header does not exist.
     *
     * @return The first header value or {@code defaultValue} if there is no such header
     */
    public abstract String getHeader(CharSequence name, String defaultValue);

    /**
     * Returns the values of headers with the specified name
     *
     * @param name The name of the headers to search
     *
     * @return A {@link List} of header values which will be empty if no values are found
     */
    public abstract List<String> getAllHeaderValues(CharSequence name);

    /**
     * Returns the date header value with the specified header name.  If there are more than one header value for the
     * specified header name, the first value is returned.
     * The value is parsed as per the
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a> using the format:
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name The name of the header to search
     *
     * @return the header value
     *
     * @throws ParseException if there is no such header or the header value is not a formatted date
     */
    public abstract long getDateHeader(CharSequence name) throws ParseException;

    /**
     * Returns the date header value with the specified header name.  If there are more than one header value for the
     * specified header name, the first value is returned.
     * The value is parsed as per the
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a> using the format:
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name The name of the header to search
     * @param defaultValue Default value if there is no header with this name.
     *
     * @return the header value or {@code defaultValue} if there is no header with this name.
     */
    public abstract long getDateHeader(CharSequence name, long defaultValue);

    /**
     * Returns the integer header value with the specified header name.  If there are more than one header value for
     * the specified header name, the first value is returned.
     *
     * @param name The name of the header to search
     *
     * @return the header value
     *
     * @throws NumberFormatException if there is no such header or the header value is not a number
     */
    public abstract int getIntHeader(CharSequence name);

    /**
     * Returns the integer header value with the specified header name.  If there are more than one header value for
     * the specified header name, the first value is returned.
     *
     * @param name The name of the header to search
     * @param defaultValue Default if the header does not exist.
     *
     * @return the header value or the {@code defaultValue} if there is no such header or the header value is not a
     * number
     */
    public abstract int getIntHeader(CharSequence name, int defaultValue);

    /**
     * Returns a new {@link Set} that contains the names of all headers in this response.  Note that modifying the
     * returned {@link Set} will not affect the state of this response.
     */
    public abstract Set<String> getHeaderNames();

    /**
     * Adds an HTTP header with the passed {@code name} and {@code value} to this response.
     *
     * @param name Name of the header.
     * @param value Value for the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> addHeader(CharSequence name, Object value);

    /**
     * Adds the passed {@code cookie} to this response.
     *
     * @param cookie Cookie to add.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> addCookie(Cookie cookie);

    /**
     * Adds the passed header as a date value to this response. The date is formatted using netty's
     * {@link HttpHeaders#addDateHeader(HttpMessage, CharSequence, Date)} which formats the date as per the
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a> into the format:
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name Name of the header.
     * @param value Value of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> addDateHeader(CharSequence name, Date value);

    /**
     * Adds multiple date values for the passed header name to this response. The date values are formatted using netty's
     * {@link HttpHeaders#addDateHeader(HttpMessage, CharSequence, Date)} which formats the date as per the
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a> into the format:
     *
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name Name of the header.
     * @param values Values for the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> addDateHeader(CharSequence name, Iterable<Date> values);

    /**
     * Adds an HTTP header with the passed {@code name} and {@code values} to this response.
     *
     * @param name Name of the header.
     * @param values Values for the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> addHeader(CharSequence name, Iterable<Object> values);

    /**
     * Overwrites the current value, if any, of the passed header to the passed date value for this response. The date is
     * formatted using netty's {@link HttpHeaders#addDateHeader(HttpMessage, CharSequence, Date)} which formats the date
     * as per the <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a> into
     * the format:
     *
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name Name of the header.
     * @param value Value of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setDateHeader(CharSequence name, Date value);

    /**
     * Overwrites the current value, if any, of the passed header to the passed value for this response.
     *
     * @param name Name of the header.
     * @param value Value of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setHeader(CharSequence name, Object value);

    /**
     * Overwrites the current value, if any, of the passed header to the passed date values for this response. The date
     * is formatted using netty's {@link HttpHeaders#addDateHeader(HttpMessage, CharSequence, Date)} which formats the
     * date as per the <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.3.1">HTTP specifications</a>
     * into the format:
     *
     * <PRE>"E, dd MMM yyyy HH:mm:ss z"</PRE>
     *
     * @param name Name of the header.
     * @param values Values of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setDateHeader(CharSequence name, Iterable<Date> values);

    /**
     * Overwrites the current value, if any, of the passed header to the passed values for this response.
     *
     * @param name Name of the header.
     * @param values Values of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setHeader(CharSequence name, Iterable<Object> values);

    /**
     * Removes the passed header from this response.
     *
     * @param name Name of the header.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> removeHeader(CharSequence name);

    /**
     * Sets the status for the response.
     *
     * @param status Status to set.
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setStatus(HttpResponseStatus status);

    /**
     * Sets the HTTP transfer encoding to chunked for this response. This delegates to
     * {@link HttpHeaders#setTransferEncodingChunked(HttpMessage)}
     *
     * @return {@code this}
     */
    public abstract HttpServerResponse<C> setTransferEncodingChunked();

    /**
     * This is a performance optimization to <em>not</em> flush the channel on every response send.
     *
     * This can be used
     * only when the processing for a server is not asynchronous, in which case, one would have to flush the responses
     * written explicitly (done on completion of the {@link Observable} written). Something like this:
     *
     <PRE>
     resp.sendHeaders()
         .writeStringAndFlushOnEach(Observable.interval(1, TimeUnit.SECONDS))
                                              .map(aLong -$gt; "Interval =&gt;" + aLong)
                                   )
     </PRE>
     *
     * This can be used when the response is written synchronously from a {@link RequestHandler}, something like:
     *
     <PRE>
     response.writeString(Observable.just("Hello world");
     </PRE>
     *
     * When set, this will make the channel to be flushed only when all the requests available on the channel are
     * read. Thus, making it possible to do a gathering write for all pipelined requests on a connection. This reduces
     * the number of system calls and is helpful in "Hello World" benchmarks.
     */
    public abstract HttpServerResponse<C> flushOnlyOnReadComplete();

    /**
     * Sends the headers for this response when the returned {@code Observable} is subscribed. Alternatively, one can
     * continue to write contents using the returned {@link ResponseContentWriter}
     *
     * @return {@link ResponseContentWriter} which can be subscribed to only send the headers or to write payload.
     */
    public abstract ResponseContentWriter<C> sendHeaders();

    /**
     * Converts this response to enable writing {@link ServerSentEvent}s.
     *
     * @return This response with writing of {@link ServerSentEvent} enabled.
     */
    @Experimental
    public abstract HttpServerResponse<ServerSentEvent> transformToServerSentEvents();

    /**
     * Creates a new {@code HttpServerResponse} instance modifying the content type using the passed {@code transformer}.
     *
     * @param transformer Transformer to transform the content stream.
     *
     * @param <CC> New type of the content.
     *
     * @return A new instance of {@link HttpServerResponse} with the transformed content stream.
     */
    public abstract <CC> HttpServerResponse<CC> transformContent(AllocatingTransformer<CC, C> transformer);

    /**
     * Accepts the upgrade to websockets, if requested and after sending a successful handshake response,
     * invokes the passed handler to handle the websocket connection.
     *
     * If any changes to this response are required for the handshake, they should be done before invoking this method.
     *
     * @return {@link WebSocketHandshaker} for sending a handshake to the client. Subscription to the handshaker, will
     * send the handshake.
     */
    public abstract WebSocketHandshaker acceptWebSocketUpgrade(WebSocketHandler handler);

    /**
     * Disposes this response. If the response is not yet set then this will attempt to send an error response if the
     * connection is still open.
     *
     * @return An {@link Observable}, subscription to which will dispose this response.
     */
    public abstract Observable<Void> dispose();

    /**
     * Returns the underlying channel on which this response was received.
     *
     * @return The underlying channel on which this response was received.
     */
    public abstract Channel unsafeNettyChannel();

    /**
     * Returns the underlying connection on which this response was received.
     *
     * @return The underlying connection on which this response was received.
     */
    public abstract Connection<?, ?> unsafeConnection();
}
