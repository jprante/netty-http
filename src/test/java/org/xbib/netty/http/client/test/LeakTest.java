package org.xbib.netty.http.client.test;

import org.junit.After;
import org.junit.Test;
import org.xbib.netty.http.client.Client;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LeakTest {

    private static final Logger logger = Logger.getLogger(LeakTest.class.getName());

    @After
    public void checkThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        logger.log(Level.INFO, "threads = " + threadSet.size() );
        threadSet.forEach( thread -> logger.log(Level.INFO, thread.toString()));
    }

    @Test
    public void testForLeaks() throws IOException, InterruptedException {
        Client client = new Client();
        client.shutdownGracefully();
    }
}
