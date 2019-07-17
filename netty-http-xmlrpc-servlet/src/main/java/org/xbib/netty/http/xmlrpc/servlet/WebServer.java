package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcStreamServer;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * <p>The {@link WebServer} is a minimal HTTP server, that might be used
 * as an embedded web server.</p>
 * <p>Use of the {@link WebServer} has grown very popular amongst users
 * of Apache XML-RPC. Why this is the case, can hardly be explained,
 * because the {@link WebServer} is at best a workaround, compared to
 * full blown servlet engines like Tomcat or Jetty. For example, under
 * heavy load it will almost definitely be slower than a real servlet
 * engine, because it does neither support proper keepalive (multiple
 * requests per physical connection) nor chunked mode (in other words,
 * it cannot stream requests).</p>
 * <p>If you still insist in using the {@link WebServer}, it is
 * recommended to use its subclass, the {@link ServletWebServer} instead,
 * which offers a minimal subset of the servlet API. In other words,
 * you keep yourself the option to migrate to a real servlet engine
 * later.</p>
 * <p>Use of the {@link WebServer} goes roughly like this: First of all,
 * create a property file (for example "MyHandlers.properties") and
 * add it to your jar file. The property keys are handler names and
 * the property values are the handler classes. Once that is done,
 * create an instance of WebServer:
 * <pre>
 *   final int port = 8088;
 *   final String propertyFile = "MyHandler.properties";
 *
 *   PropertyHandlerMapping mapping = new PropertyHandlerMapping();
 *   ClassLoader cl = Thread.currentThread().getContextClassLoader();
 *   mapping.load(cl, propertyFile);
 *   WebServer webServer = new WebServer(port);
 *   XmlRpcServerConfigImpl config = new XmlRpcServerConfigImpl();
 *   XmlRpcServer server = webServer.getXmlRpcServer();
 *   server.setConfig(config);
 *   server.setHandlerMapping(mapping);
 *   webServer.start();
 * </pre>
 */
public class WebServer implements Runnable {

    protected ServerSocket serverSocket;

    private final WebServerThread webServerThread;

    private ThreadPool pool;

    protected final List<AddressMatcher> accept = new ArrayList<>();

    protected final List<AddressMatcher> deny = new ArrayList<>();

    protected final XmlRpcStreamServer server = newXmlRpcStreamServer();

    protected XmlRpcStreamServer newXmlRpcStreamServer(){
        return new ConnectionServer();
    }

    // Inputs to setupServerSocket()
    private InetAddress address;

    private int port;

    private boolean paranoid;

    static final String HTTP_11 = "HTTP/1.1";
    /** Creates a web server at the specified port number.
     * @param pPort Port number; 0 for a random port, choosen by the
     * operating system.
     */
    public WebServer(int pPort) {
        this(pPort, null);
    }

    /** Creates a web server at the specified port number and IP address.
     * @param pPort Port number; 0 for a random port, choosen by the
     * operating system.
     * @param pAddr Local IP address; null for all available IP addresses.
     */
    public WebServer(int pPort, InetAddress pAddr) {
        address = pAddr;
        port = pPort;
        webServerThread = new WebServerThread(this, "XML-RPC Weblistener");
    }

    /**
     * Factory method to manufacture the server socket.  Useful as a
     * hook method for subclasses to override when they desire
     * different flavor of socket (i.e. a <code>SSLServerSocket</code>).
     *
     * @param pPort Port number; 0 for a random port, choosen by the operating
     * system.
     * @param backlog
     * @param addr If <code>null</code>, binds to
     * <code>INADDR_ANY</code>, meaning that all network interfaces on
     * a multi-homed host will be listening.
     * @exception IOException Error creating listener socket.
     */
    protected ServerSocket createServerSocket(int pPort, int backlog, InetAddress addr)
            throws IOException {
        return new ServerSocket(pPort, backlog, addr);
    }

    /**
     * Initializes this server's listener socket with the specified
     * attributes, assuring that a socket timeout has been set.  The
     * {@link #createServerSocket(int, int, InetAddress)} method can
     * be overridden to change the flavor of socket used.
     *
     * @see #createServerSocket(int, int, InetAddress)
     */
    private synchronized void setupServerSocket(int backlog) throws IOException {
        serverSocket = createServerSocket(port, backlog, address);
        // A socket timeout must be set.
        if (serverSocket.getSoTimeout() <= 0) {
            serverSocket.setSoTimeout(4096);
        }
    }

    /**
     * Spawns a new thread which binds this server to the port it's
     * configured to accept connections on.
     *
     * @see #run()
     * @throws IOException Binding the server socket failed.
     */
    public void start() throws IOException {
        setupServerSocket(50);
        // Not marked as daemon thread since run directly via main().
        webServerThread.start();
    }

    /**
     * Switch client filtering on/off.
     * @param pParanoid True to enable filtering, false otherwise.
     * @see #acceptClient(String)
     * @see #denyClient(String)
     */
    public void setParanoid(boolean pParanoid) {
        paranoid = pParanoid;
    }

    /**
     * Returns the client filtering state.
     * @return True, if client filtering is enabled, false otherwise.
     * @see #acceptClient(String)
     * @see #denyClient(String)
     */
    protected boolean isParanoid() {
        return paranoid;
    }

    /** Add an IP address to the list of accepted clients. The parameter can
     * contain '*' as wildcard character, e.g. "192.168.*.*". You must call
     * setParanoid(true) in order for this to have any effect.
     * @param pAddress The IP address being enabled.
     * @see #denyClient(String)
     * @see #setParanoid(boolean)
     * @throws IllegalArgumentException Parsing the address failed.
     */
    public void acceptClient(String pAddress) {
        accept.add(new AddressMatcher(pAddress));
    }

    /**
     * Add an IP address to the list of denied clients. The parameter can
     * contain '*' as wildcard character, e.g. "192.168.*.*". You must call
     * setParanoid(true) in order for this to have any effect.
     * @param pAddress The IP address being disabled.
     * @see #acceptClient(String)
     * @see #setParanoid(boolean)
     * @throws IllegalArgumentException Parsing the address failed.
     */
    public void denyClient(String pAddress) {
        deny.add(new AddressMatcher(pAddress));
    }

    /**
     * Checks incoming connections to see if they should be allowed.
     * If not in paranoid mode, always returns true.
     *
     * @param s The socket to inspect.
     * @return Whether the connection should be allowed.
     */
    protected boolean allowConnection(Socket s) {
        if (!paranoid) {
            return true;
        }
        int l = deny.size();
        byte[] addr = s.getInetAddress().getAddress();
        for (int i = 0; i < l; i++) {
            AddressMatcher match = deny.get(i);
            if (match.matches(addr)) {
                return false;
            }
        }
        l = accept.size();
        for (int i = 0; i < l; i++) {
            AddressMatcher match = accept.get(i);
            if (match.matches(addr)) {
                return true;
            }
        }
        return false;
    }

    protected Runnable newTask(WebServer pServer, XmlRpcStreamServer pXmlRpcServer,
                               Socket pSocket) throws IOException {
        return new Connection(pServer, pXmlRpcServer, pSocket);
    }

    /**
     * Listens for client requests until stopped.  Call {@link
     * #start()} to invoke this method, and {@link #shutdown()} to
     * break out of it.
     *
     * @throws RuntimeException Generally caused by either an
     * <code>UnknownHostException</code> or <code>BindException</code>
     * with the vanilla web server.
     *
     * @see #start()
     * @see #shutdown()
     */
    @Override
    public void run() {
        pool = newThreadPool();
        try {
            while (!webServerThread.closed) {
                if (serverSocket.isClosed()) {
                    break;
                }
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    if (allowConnection(socket)) {
                        // set read timeout to 1 seconds
                        socket.setSoTimeout(1000);
                        Runnable task = newTask(this, server, socket);
                        if (pool.startTask(task)) {
                            socket = null;
                        } else {
                            log("Maximum load of " + pool.getMaxThreads()
                                    + " exceeded, rejecting client");
                        }
                    }
                } catch (InterruptedIOException e) {
                    //
                } catch (Throwable t) {
                    log(t);
                    throw new RuntimeException(t);
                } finally {
                    if (socket != null) {
                        try {
                            log("closing client socket");
                            socket.close();
                        } catch (Throwable ignore) {

                        }
                    }
                }
            }
        } finally {
            pool.shutdown();
            if (serverSocket != null && !serverSocket.isClosed()) {
                try {
                    log("closing server socket");
                    serverSocket.close();
                } catch (IOException e) {
                    log(e);
                }
            }
        }
    }

    protected ThreadPool newThreadPool() {
        return new ThreadPool(server.getMaxThreads(), "XML-RPC");
    }

    /**
     * Stop listening on the server port.
     * Shutting down our {@link #webServerThread} effectively breaks it out of its {@link #run()} loop.
     *
     * @see #run()
     */
    public synchronized void shutdown() throws IOException {
        webServerThread.closed = true;
        webServerThread.interrupt();
        // wait for server socket down
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Thread.sleep(25L);
            } catch (InterruptedException e) {
                //
            }
        }
        serverSocket = null;
        try {
            Thread.sleep(25L);
        } catch (InterruptedException e) {
            //
        }
        log("shutdown complete");
    }

    public boolean isShutDown() {
        return serverSocket.isClosed();
    }

    /** Returns the port, on which the web server is running.
     * This method may be invoked after {@link #start()} only.
     * @return Servers port number
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /** Logs an error.
     * @param pError The error being logged.
     */
    public void log(Throwable pError) {
        String msg = pError.getMessage() == null ? pError.getClass().getName() : pError.getMessage();
        server.getErrorLogger().log(msg, pError);
    }

    /** Logs a message.
     * @param pMessage The being logged.
     */
    public void log(String pMessage) {
        server.getErrorLogger().log(pMessage);
    }

    /** Returns the {@link XmlRpcServer}.
     * @return The server object.
     */
    public XmlRpcStreamServer getXmlRpcServer() {
        return server;
    }

    private class WebServerThread extends Thread {
        volatile boolean closed = false;

        WebServerThread(Runnable runnable, String name) {
            super(runnable, name);
        }
    }

    private class AddressMatcher {

        private final int[] pattern;

        AddressMatcher(String pAddress) {
            try {
                pattern = new int[4];
                StringTokenizer st = new StringTokenizer(pAddress, ".");
                if (st.countTokens() != 4) {
                    throw new IllegalArgumentException();
                }
                for (int i = 0; i < 4; i++)	{
                    String next = st.nextToken();
                    if ("*".equals(next)) {
                        pattern[i] = 256;
                    } else {
                        pattern[i] = (byte) Integer.parseInt(next);
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("\"" + pAddress
                        + "\" does not represent a valid IP address");
            }
        }

        boolean matches(byte[] pAddress) {
            for (int i = 0; i < 4; i++)	{
                if (pattern[i] > 255) {
                    continue; // Wildcard
                }
                if (pattern[i] != pAddress[i]) {
                    return false;
                }
            }
            return true;
        }
    }

}
