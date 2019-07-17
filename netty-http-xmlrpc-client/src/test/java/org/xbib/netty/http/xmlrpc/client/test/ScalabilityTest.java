package org.xbib.netty.http.xmlrpc.client.test;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import junit.framework.TestCase;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.server.PropertyHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.servlet.ServletWebServer;
import org.xbib.netty.http.xmlrpc.servlet.ThreadPool;
import org.xbib.netty.http.xmlrpc.servlet.WebServer;
import org.xbib.netty.http.xmlrpc.servlet.XmlRpcServlet;

/**
 * Tests the frameworks scalability.
 */
public class ScalabilityTest extends TestCase {

    private static final Logger logger = Logger.getLogger(ScalabilityTest.class.getName());

    /**
     * Primitive handler class
     */
    public static class Adder {
        /**
         * Returns the sum of the numbers p1 and p2.
         */
        public int add(int p1, int p2) {
            return p1 + p2;
        }
    }

    private static final int BASE = 1;

    private static final Integer THREE = 3;

    private static final Integer FIVE = 5;

    private static final Integer EIGHT = 8;

    private XmlRpcServlet servlet;

    private MyServletWebServer server;

    private MyWebServer webServer;

    private XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
        PropertyHandlerMapping mapping = new PropertyHandlerMapping();
        mapping.addHandler("Adder", Adder.class);
        return mapping;
    }

    private void initServletWebServer() throws Exception {
        servlet = new XmlRpcServlet() {

            private static final long serialVersionUID = -2040521497373327817L;

            @Override
            protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
                return ScalabilityTest.this.newXmlRpcHandlerMapping();
            }
        };
        server = new MyServletWebServer(servlet, 8080);
        server.getXmlRpcServer().setMaxThreads(25);
        server.start();
    }

    private void shutdownServletWebServer() throws IOException {
        server.shutdown();
    }

    private void initWebServer() throws Exception {
        webServer = new MyWebServer(8080);
        webServer.getXmlRpcServer().setHandlerMapping(newXmlRpcHandlerMapping());
        webServer.getXmlRpcServer().setMaxThreads(25);
        webServer.start();
    }

    private void shutdownWebServer() throws IOException {
        webServer.shutdown();
    }

    /**
     * Runs the servlet test with a single client.
     */
    public void testSingleServletClient() throws Exception {
        initServletWebServer();
        try {
            long now = System.currentTimeMillis();
            servlet.getXmlRpcServletServer().setMaxThreads(1);
            new MyClient(100 * BASE, server.getPort()).run();
            logger.log(Level.INFO,
                    "Single servlet client: " + (System.currentTimeMillis() - now) + ", " + server.getNumThreads());
        } finally {
            shutdownServletWebServer();
        }
    }

    /**
     * Runs the web server test with a single client.
     */
    public void testSingleWebServerClient() throws Exception {
        initWebServer();
        try {
            long now = System.currentTimeMillis();
            webServer.getXmlRpcServer().setMaxThreads(1);
            new MyClient(100 * BASE, webServer.getPort()).run();
            logger.log(Level.INFO,
                    "Single web server client: " + (System.currentTimeMillis( ) -now) + ", " + webServer.getNumThreads());
        } finally {
            shutdownWebServer();
        }
    }

    /**
     * Runs the test with ten clients.
     */
    public void testTenClient() throws Exception {
        initServletWebServer();
        try {
            final Thread[] threads = new Thread[10];
            servlet.getXmlRpcServletServer().setMaxThreads(10);
            long now = System.currentTimeMillis();
            for (int i = 0;  i < threads.length;  i++) {
                threads[i] = new Thread(new MyClient(10 * BASE, server.getPort()));
                threads[i].start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            logger.log(Level.INFO, "Ten clients: " + (System.currentTimeMillis() - now) + ", " + server.getNumThreads());
            shutdownServletWebServer();
        } finally {
            shutdownServletWebServer();
        }
    }

    private static class MyClient implements Runnable {

        private final int iterations;

        private final int port;

        MyClient(int pIterations, int pPort) {
            iterations = pIterations;
            port = pPort;
        }

        @Override
        public void run() {
            int i = 0;
            try {
                XmlRpcClient client = new XmlRpcClient();
                XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
                config.setServerURL(new URL("http://localhost:" + port + "/"));
                client.setConfig(config);
                for (i = 0;  i < iterations;  i++) {
                    assertEquals(EIGHT, client.execute("Adder.add", new Object[] {
                            THREE, FIVE
                    }));
                }
            } catch (Throwable t) {
                throw new RuntimeException("i=" + i, t);
            }
        }
    }

    private class MyServletWebServer extends ServletWebServer {

        ThreadPool pool;

        MyServletWebServer(HttpServlet pServlet, int pPort) throws ServletException {
            super(pServlet, pPort);
        }

        @Override
        public ThreadPool newThreadPool(){
            pool = new ThreadPool(getXmlRpcServer().getMaxThreads(), "XML-RPC");
            return pool;
        }

        int getNumThreads() {
            return pool.getNumThreads();
        }
    }

    private class MyWebServer extends WebServer {

        ThreadPool pool;

        MyWebServer(int pPort) {
            super(pPort);
        }

        @Override
        public ThreadPool newThreadPool(){
            pool = new ThreadPool(getXmlRpcServer().getMaxThreads(), "XML-RPC");
            return pool;
        }

        int getNumThreads() {
            return pool.getNumThreads();
        }
    }

}
