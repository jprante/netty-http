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
package io.reactivex.netty.protocol.tcp.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.reactivex.netty.client.pool.PooledConnection;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.AssertableSubscriber;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static rx.Observable.fromCallable;
import static rx.Observable.just;

/**
 * This tests the code paths which are not invoked for {@link EmbeddedChannel} as it does not schedule any task
 * (an EmbeddedChannelEventLopp never returns false for isInEventLoop())
 */
@Ignore
public class PoolingWithRealChannelTest {

    @Rule
    public final TcpClientRule clientRule = new TcpClientRule();

    /**
     * This test validates the async onNext and synchronous onComplete/onError nature of pooling when the connection is
     * reused.
     */
    //@Test(timeout = 60000)
    public void testReuse() throws Exception {
        clientRule.startServer(1);
        PooledConnection<ByteBuf, ByteBuf> connection = clientRule.connect();
        connection.closeNow();
        assertThat("Pooled connection is closed.", connection.unsafeNettyChannel().isOpen(), is(true));
        PooledConnection<ByteBuf, ByteBuf> connection2 = clientRule.connect();
        assertThat("Connection is not reused.", connection2, is(connection));
    }

    /**
     * Load test to prove concurrency issues mainly seen on heavy load.
     */
    @Test
    public void testLoad() {
        clientRule.startServer(1000);
        MockTcpClientEventListener listener = new MockTcpClientEventListener();
        clientRule.getClient().subscribe(listener);
        int number_of_iterations = 10; // 300
        int numberOfRequests = 2; // 10
        for(int j = 0; j < number_of_iterations; j++) {
            List<Observable<String>> results = new ArrayList<>();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for (int i = 0; i < numberOfRequests; i++) {
                results.add(
                    fromCallable((Func0<PooledConnection<ByteBuf, ByteBuf>>) clientRule::connectWithCheck)
                        .flatMap((Func1<PooledConnection<ByteBuf, ByteBuf>, Observable<String>>) connection ->
                                connection.writeStringAndFlushOnEach(just("Hello"))
                            .toCompletable()
                            .<ByteBuf>toObservable()
                            .concatWith(connection.getInput())
                            .take(1)
                            .single()
                            .map(byteBuf -> {
                                try {
                                    byte[] bytes = new byte[byteBuf.readableBytes()];
                                    byteBuf.readBytes(bytes);
                                    return new String(bytes);
                                } finally {
                                    byteBuf.release();
                                }
                            }).doOnError(throwable -> {
                                Assert.fail("Did not expect exception: " + throwable.getMessage());
                                throwable.printStackTrace();
                            })));
            }
            AssertableSubscriber<String> test = Observable.merge(results).test();
            test.awaitTerminalEvent();
            test.assertNoErrors();
        }
    }

    /**
     *
     * Load test to prove concurrency issues mainly seen on heavy load.
     *
     */
    @Test
    public void assertPermitsAreReleasedWhenMergingObservablesWithExceptions() {
        clientRule.startServer(10, true);
        MockTcpClientEventListener listener = new MockTcpClientEventListener();
        clientRule.getClient().subscribe(listener);
        int number_of_iterations = 1;
        int numberOfRequests = 3;
        makeRequests(number_of_iterations, numberOfRequests);
        sleep(clientRule.getPoolConfig().getMaxIdleTimeMillis());
        assertThat("Permits should be 10",
                clientRule.getPoolConfig().getPoolLimitDeterminationStrategy().getAvailablePermits(), equalTo(10));
    }

    private void sleep(long i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void makeRequests(int number_of_iterations, int numberOfRequests) {
        for (int j = 0; j < number_of_iterations; j++) {
            sleep(100);
            List<Observable<String>> results = new ArrayList<>();
            //Just giving the client some time to recover
            sleep(100);
            for (int i = 0; i < numberOfRequests; i++) {
                results.add(
                    fromCallable((Func0<PooledConnection<ByteBuf, ByteBuf>>) clientRule::connect)
                        .flatMap((Func1<PooledConnection<ByteBuf, ByteBuf>, Observable<String>>) connection ->
                                connection.writeStringAndFlushOnEach(just("Hello"))
                            .toCompletable()
                            .<ByteBuf>toObservable()
                            .concatWith(connection.getInput())
                            .take(1)
                            .single()
                            .map((Func1<ByteBuf, String>) byteBuf -> {
                                try {
                                    byte[] bytes = new byte[byteBuf.readableBytes()];
                                    byteBuf.readBytes(bytes);
                                    return new String(bytes);
                                } finally {
                                    byteBuf.release();
                                }
                            })));
            }
            AssertableSubscriber<String> test = Observable.merge(results).test();
            test.awaitTerminalEvent();
        }
    }
}
