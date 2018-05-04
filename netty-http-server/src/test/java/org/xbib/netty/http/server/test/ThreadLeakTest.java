package org.xbib.netty.http.server.test;

import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.After;
import org.junit.Test;
import org.xbib.TestBase;
import org.xbib.netty.http.server.Server;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ThreadLeakTest extends TestBase {

    private static final Logger logger = Logger.getLogger(ThreadLeakTest.class.getName());

    @Test
    public void testForLeaks() throws IOException {
        Server server = Server.builder()
                .setByteBufAllocator(UnpooledByteBufAllocator.DEFAULT)
                .build();
        server.getDefaultVirtualServer().addContext("/", (request, response) ->
                response.write("Hello World"));
        try {
            server.accept();
        } finally {
            server.shutdownGracefully();
        }
    }

    @After
    public void checkThreads() throws Exception {
        Thread.sleep(1000L);
        System.gc();
        Thread.sleep(3000L);
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        logger.log(Level.INFO, "threads = " + threadSet.size() );
        threadSet.forEach( thread -> logger.log(Level.INFO, thread.toString()));
    }
}
