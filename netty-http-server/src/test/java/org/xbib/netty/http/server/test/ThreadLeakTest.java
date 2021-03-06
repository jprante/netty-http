package org.xbib.netty.http.server.test;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.HttpServerDomain;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(NettyHttpTestExtension.class)
class ThreadLeakTest {

    private static final Logger logger = Logger.getLogger(ThreadLeakTest.class.getName());

    @Test
    void testForLeaks() throws IOException {
        HttpServerDomain domain = HttpServerDomain.builder(HttpAddress.http1("localhost", 8008))
                .singleEndpoint("/", (request, response) ->
                        response.write("Hello World"))
                .build();
        Server server = Server.builder(domain)
                .setByteBufAllocator(UnpooledByteBufAllocator.DEFAULT)
                .build();
        try {
            server.accept();
        } finally {
            server.shutdownGracefully();
        }
    }

    @AfterAll
    void checkThreads() throws Exception {
        Thread.sleep(1000L);
        System.gc();
        Thread.sleep(3000L);
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        logger.log(Level.INFO, "threads = " + threadSet.size() );
        threadSet.forEach( thread -> logger.log(Level.INFO, thread.toString()));
    }
}
