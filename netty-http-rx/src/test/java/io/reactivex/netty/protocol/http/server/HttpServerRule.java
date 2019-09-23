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
package io.reactivex.netty.protocol.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.protocol.http.client.HttpClient;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.junit.Assert;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

public class HttpServerRule extends ExternalResource {

    public static final String WELCOME_SERVER_MSG = "Welcome!";

    private HttpServer<ByteBuf, ByteBuf> server;
    private HttpClient<ByteBuf, ByteBuf> client;

    private String lastResponse = "";

    @Override
    public Statement apply(final Statement base, Description description) {
        lastResponse = "";
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                server = HttpServer.newServer()
                    .enableWireLogging("test", LogLevel.INFO)
                    .addChannelHandlerFirst("raw-message-handler",
                        RawMessageHandler.factory(
                            new Action1<ByteBuf>() {
                                @Override
                                public void call(ByteBuf byteBuf) {
                                    lastResponse += byteBuf.toString(Charset.defaultCharset());
                                }
                            }
                        )
                    );
                base.evaluate();
            }
        };
    }

    public SocketAddress startServer() {
        server.start(new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request,
                                           HttpServerResponse<ByteBuf> response) {
                return response.setHeader(CONTENT_LENGTH, WELCOME_SERVER_MSG.getBytes().length)
                               .writeString(Observable.just(WELCOME_SERVER_MSG));
            }
        });
        client = HttpClient.newClient("127.0.0.1", server.getServerPort());
        return server.getServerAddress();
    }

    public void startServer(RequestHandler<ByteBuf, ByteBuf> handler) {
        server.start(handler);
        client = HttpClient.newClient("127.0.0.1", server.getServerPort());
    }

    public void setupClient(HttpClient<ByteBuf, ByteBuf> client) {
        this.client = client;
    }

    public HttpClientResponse<ByteBuf> sendRequest(Observable<HttpClientResponse<ByteBuf>> request) {
        TestSubscriber<HttpClientResponse<ByteBuf>> subscriber = new TestSubscriber<>();

        request.subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();

        assertThat("Unexpected response count.", subscriber.getOnNextEvents(), hasSize(1));
        return subscriber.getOnNextEvents().get(0);
    }

    public void assertResponseContent(HttpClientResponse<ByteBuf> response) {
        TestSubscriber<ByteBuf> subscriber = new TestSubscriber<>();

        response.getContent().subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        subscriber.assertNoErrors();

        assertThat("Unexpected content items.", subscriber.getOnNextEvents(), hasSize(1));
        assertThat("Unexpected content.", subscriber.getOnNextEvents().get(0).toString(Charset.defaultCharset()),
                   equalTo(WELCOME_SERVER_MSG));
    }

    public void assertEmptyBodyWithContentLengthZero() {
        assertBodyWithContentLength(0, "");
    }

    public void assertBodyWithContentLength(int contentLength, String body) {
        getAndDrainClient();
        Pattern headerBlock = Pattern.compile("^(.*?\r\n)*?\r\n", Pattern.MULTILINE);

        if (!lastResponse.contains("content-length: " + contentLength + "\r\n")) {
            Assert.fail("Missing header 'content-length: " + contentLength + "'");
        }
        if (lastResponse.contains("transfer-encoding: chunked\r\n")) {
            Assert.fail("Unexpected header 'transfer-encoding: chunked'");
        }
        if (!headerBlock.matcher(lastResponse).replaceFirst("").equals(body)) {
            Assert.fail("Unexpected body content '" + headerBlock.matcher(lastResponse).replaceFirst("") + "'");
        }
    }

    public void assertEmptyBodyWithSingleChunk() {
        assertChunks();
    }

    public void assertChunks(String... chunks) {
        getAndDrainClient();
        Pattern headerBlock = Pattern.compile("^(.*?\r\n)*?\r\n", Pattern.MULTILINE);

        if (lastResponse.contains("content-length: 0\r\n")) {
            Assert.fail("Unexpected header 'content-length: 0'");
        }
        if (!lastResponse.contains("transfer-encoding: chunked\r\n")) {
            Assert.fail("Missing header 'transfer-encoding: chunked'");
        }
        String expectedChunkContent = "";
        for (String c : chunks) {
            expectedChunkContent += c.getBytes().length + "\r\n";
            expectedChunkContent += c + "\r\n";
        }
        expectedChunkContent += "0\r\n\r\n";
        if (!headerBlock.matcher(lastResponse).replaceFirst("").equals(expectedChunkContent)) {
            Assert.fail("Unexpected body content '" + headerBlock.matcher(lastResponse).replaceFirst("") + "'");
        }
    }

    public SocketAddress getServerAddress() {
        return new InetSocketAddress("127.0.0.1", server.getServerPort());
    }

    public void setServer(HttpServer<ByteBuf, ByteBuf> server) {
        this.server = server;
    }

    public HttpServer<ByteBuf, ByteBuf> getServer() {
        return server;
    }

    public HttpClient<ByteBuf, ByteBuf> getClient() {
        return client;
    }


    public void getAndDrainClient() {
        lastResponse = "";
        TestSubscriber<Void> clientDrain = new TestSubscriber<>();
        client.createGet("/")
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(HttpClientResponse<ByteBuf> clientResponse) {
                        return clientResponse.discardContent();
                    }
                })
                .subscribe(clientDrain);
        clientDrain.awaitTerminalEvent();
        clientDrain.assertNoErrors();
    }


    private static class RawMessageHandler extends ChannelDuplexHandler {

        public static Func0<ChannelHandler> factory(final Action1<ByteBuf> onWrite) {
            return new Func0<ChannelHandler>() {
                @Override
                public ChannelHandler call() {
                    return new RawMessageHandler(onWrite);
                }
            };
        }

        private final Action1<ByteBuf> onWrite;

        public RawMessageHandler(Action1<ByteBuf> onWrite) {
            this.onWrite = onWrite;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            callback(msg, onWrite);
            super.write(ctx, msg, promise);
        }

        private void callback(Object msg, Action1<ByteBuf> a) {
            if (msg instanceof ByteBuf) {
                a.call((ByteBuf) msg);
            } else if (msg instanceof ByteBufHolder) {
                a.call(((ByteBufHolder) msg).content());
            } else {
                throw new RuntimeException("Unexpected msg type " + msg.getClass());
            }
        }
    }
}
