package org.xbib.netty.http.client.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ExtendWith(NettyHttpExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ThreadLeakTest {

    private static final Logger logger = Logger.getLogger(ThreadLeakTest.class.getName());

    @Test
    void testForLeaks() throws IOException {
        Client client = new Client();
        client.shutdownGracefully();
    }

    @AfterAll
    void checkThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        logger.log(Level.INFO, "threads = " + threadSet.size() );
        threadSet.forEach( thread -> logger.log(Level.INFO, thread.toString()));
    }
}
