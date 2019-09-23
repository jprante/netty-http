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
package io.reactivex.netty.channel;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.reactivex.netty.channel.DetachedChannelPipeline.HandlerHolder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import rx.functions.Action1;
import rx.functions.Func0;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class DetachedChannelPipelineTest {

    private static final EventLoopGroup MULTI_GRP = new NioEventLoopGroup();

    public static final Func0<ChannelHandler> HANDLER_FACTORY = new Func0<ChannelHandler>() {
        @Override
        public ChannelHandler call() {
            return new ChannelDuplexHandler();
        }
    };
    private static final HandlerHolder HANDLER_1_NO_NAME = new HandlerHolder(HANDLER_FACTORY);
    private static final HandlerHolder HANDLER_1 = new HandlerHolder("handler-1", HANDLER_FACTORY);
    private static final HandlerHolder HANDLER_1_GRP =
            new HandlerHolder("handler-1", HANDLER_FACTORY, new NioEventLoopGroup());
    private static final HandlerHolder HANDLER_1_GRP_NO_NAME =
            new HandlerHolder(null, HANDLER_FACTORY, MULTI_GRP);

    private static final HandlerHolder HANDLER_2_NO_NAME = new HandlerHolder(HANDLER_FACTORY);
    private static final HandlerHolder HANDLER_2 = new HandlerHolder("handler-2", HANDLER_FACTORY);
    private static final HandlerHolder HANDLER_2_GRP =
            new HandlerHolder("handler-2", HANDLER_FACTORY, new NioEventLoopGroup());
    private static final HandlerHolder HANDLER_2_GRP_NO_NAME =
            new HandlerHolder(null, HANDLER_FACTORY, MULTI_GRP);

    @Rule
    public final PipelineRule pipelineRule = new PipelineRule();

    @Test(timeout = 60000)
    public void testCopy() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1.getNameIfConfigured(), HANDLER_1.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addLast(HANDLER_2.getNameIfConfigured(), HANDLER_2.getHandlerFactoryIfConfigured());

        DetachedChannelPipeline copy = pipelineRule.pipeline.copy();
        assertThat("Copy did not create a new instance.", copy, not(pipelineRule.pipeline));

        assertThat("Unexpected handlers count in the copy.", copy.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers in the copy.", copy.getHoldersInOrder(),
                   contains(pipelineRule.pipeline.getHoldersInOrder().toArray()));
    }

    @Test(timeout = 60000)
    public void testAddFirst() throws Exception {
        pipelineRule.pipeline.addFirst(HANDLER_1.getNameIfConfigured(), HANDLER_1.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addFirst(HANDLER_2.getNameIfConfigured(), HANDLER_2.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_2, HANDLER_1));
    }

    @Test(timeout = 60000)
    public void testAddFirstWithGroup() throws Exception {
        pipelineRule.pipeline.addFirst(HANDLER_1_GRP.getGroupIfConfigured(), HANDLER_1_GRP.getNameIfConfigured(),
                                       HANDLER_1_GRP.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addFirst(HANDLER_2_GRP.getGroupIfConfigured(), HANDLER_2_GRP.getNameIfConfigured(),
                                       HANDLER_2_GRP.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_2_GRP, HANDLER_1_GRP));
    }

    @Test(timeout = 60000)
    public void testAddLast() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1.getNameIfConfigured(), HANDLER_1.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addLast(HANDLER_2.getNameIfConfigured(), HANDLER_2.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1, HANDLER_2));
    }

    @Test(timeout = 60000)
    public void testAddLastWithGroup() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1_GRP.getGroupIfConfigured(), HANDLER_1_GRP.getNameIfConfigured(),
                                      HANDLER_1_GRP.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addLast(HANDLER_2_GRP.getGroupIfConfigured(), HANDLER_2_GRP.getNameIfConfigured(),
                                      HANDLER_2_GRP.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_GRP, HANDLER_2_GRP));
    }

    @Test(timeout = 60000)
    public void testAddBefore() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1.getGroupIfConfigured(),
                                      HANDLER_1.getNameIfConfigured(), HANDLER_1.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addBefore(HANDLER_1.getNameIfConfigured(),
                                        HANDLER_2.getNameIfConfigured(),
                                        HANDLER_2.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_2, HANDLER_1));
    }

    @Test(timeout = 60000)
    public void testAddBeforeWithGroup() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1_GRP.getGroupIfConfigured(),
                                      HANDLER_1_GRP.getNameIfConfigured(), HANDLER_1_GRP.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addBefore(HANDLER_2_GRP.getGroupIfConfigured(),
                                        HANDLER_1_GRP.getNameIfConfigured(),
                                        HANDLER_2_GRP.getNameIfConfigured(),
                                        HANDLER_2_GRP.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_2_GRP, HANDLER_1_GRP));
    }

    @Test(timeout = 60000)
    public void testAddAfter() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1.getGroupIfConfigured(),
                                      HANDLER_1.getNameIfConfigured(), HANDLER_1.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addAfter(HANDLER_1.getNameIfConfigured(),
                                       HANDLER_2.getNameIfConfigured(),
                                       HANDLER_2.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1, HANDLER_2));
    }

    @Test(timeout = 60000)
    public void testAddAfterWithGroup() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1_GRP.getGroupIfConfigured(),
                                      HANDLER_1_GRP.getNameIfConfigured(), HANDLER_1_GRP.getHandlerFactoryIfConfigured());
        pipelineRule.pipeline.addAfter(HANDLER_2_GRP.getGroupIfConfigured(), HANDLER_1_GRP.getNameIfConfigured(),
                                       HANDLER_2_GRP.getNameIfConfigured(),
                                       HANDLER_2_GRP.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_GRP, HANDLER_2_GRP));
    }

    @Test(timeout = 60000)
    public void testAddFirstMulti() throws Exception {
        pipelineRule.pipeline.addFirst(HANDLER_1_NO_NAME.getHandlerFactoryIfConfigured(), HANDLER_2_NO_NAME.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_NO_NAME, HANDLER_2_NO_NAME));
    }

    @Test(timeout = 60000)
    public void testAddFirstMultiWithGroup() throws Exception {
        pipelineRule.pipeline.addFirst(MULTI_GRP, HANDLER_1_GRP_NO_NAME.getHandlerFactoryIfConfigured(),
                                       HANDLER_2_GRP_NO_NAME.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_GRP_NO_NAME, HANDLER_2_GRP_NO_NAME));
    }

    @Test(timeout = 60000)
    public void testAddLastMulti() throws Exception {
        pipelineRule.pipeline.addLast(HANDLER_1_NO_NAME.getHandlerFactoryIfConfigured(),
                                      HANDLER_2_NO_NAME.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_NO_NAME, HANDLER_2_NO_NAME));
    }

    @Test(timeout = 60000)
    public void testAddLastMultiWithGroup() throws Exception {
        pipelineRule.pipeline.addLast(MULTI_GRP, HANDLER_1_GRP_NO_NAME.getHandlerFactoryIfConfigured(),
                                      HANDLER_2_GRP_NO_NAME.getHandlerFactoryIfConfigured());

        assertThat("Unexpected handlers count.", pipelineRule.pipeline.getHoldersInOrder(), hasSize(2));
        assertThat("Unexpected handlers.", pipelineRule.pipeline.getHoldersInOrder(),
                   contains(HANDLER_1_GRP_NO_NAME, HANDLER_2_GRP_NO_NAME));
    }

    public static class PipelineRule extends ExternalResource {

        private DetachedChannelPipeline pipeline;
        private ChannelDuplexHandler tail;

        @Override
        public Statement apply(final Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    tail = new ChannelDuplexHandler();
                    pipeline = new DetachedChannelPipeline(new Action1<ChannelPipeline>() {
                        @Override
                        public void call(ChannelPipeline pipeline1) {
                            pipeline1.addLast(tail);
                        }
                    });
                    base.evaluate();
                }
            };
        }
    }
}