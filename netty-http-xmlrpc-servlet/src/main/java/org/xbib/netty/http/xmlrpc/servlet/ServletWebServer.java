package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.server.XmlRpcStreamServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Enumeration;
import java.util.NoSuchElementException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * <p>This is a subclass of the {@link WebServer}, which offers a minimal
 * servlet API. It is recommended to use this class, rather than the
 * {@link WebServer}, because it offers you a smooth migration path to
 * a full blown servlet engine.</p>
 * <p>Use of the {@link ServletWebServer} goes like this: First of all,
 * create a servlet. It may be an instance of {@link XmlRpcServlet} or
 * a subclass thereof. Note, that servlets are stateless: One servlet
 * may be used by multiple threads (aka requests) concurrently. In
 * other words, the servlet must not have any instance variables,
 * other than those which are read only after the servlets
 * initialization.</p>
 * <p>The XmlRpcServlet is by default using a property file named
 * <code>org/apache/xmlrpc/server/webserver/XmlRpcServlet.properties</code>.</p>
 * <pre>
 *   final int port = 8088;
 *   XmlRpcServlet servlet = new XmlRpcServlet();
 *   ServletWebServer webServer = new ServletWebServer(servlet, port);
 *   webServer.start();
 * </pre>
 */
public class ServletWebServer extends WebServer {

    private final HttpServlet servlet;

    /** Creates a new instance, which is listening on all
     * local IP addresses and the given port.
     * @param pServlet The servlet, which is handling requests.
     * @param pPort The servers port number; 0 for a random
     * port being choosen.
     * @throws ServletException Initializing the servlet failed.
     */
    public ServletWebServer(HttpServlet pServlet, int pPort) throws ServletException {
        this(pServlet, pPort, null);
    }

    /** Creates a new instance, which is listening on the
     * given IP address and the given port.
     * @param pServlet The servlet, which is handling requests.
     * @param pPort The servers port number; 0 for a random
     * port being choosen.
     * @param pAddr The servers IP address.
     * @throws ServletException Initializing the servlet failed.
     */
    public ServletWebServer(HttpServlet pServlet, int pPort, InetAddress pAddr) throws ServletException {
        super(pPort, pAddr);
        servlet = pServlet;
        servlet.init(new ServletConfig() {

            @Override
            public String getServletName() { return servlet.getClass().getName(); }

            @Override
            public ServletContext getServletContext() {
                throw new IllegalStateException("Context not available");
            }

            @Override
            public String getInitParameter(String pArg0) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return new Enumeration<String>(){

                    @Override
                    public boolean hasMoreElements() {
                        return false;
                    }

                    @Override
                    public String nextElement() {
                        throw new NoSuchElementException();
                    }
                };
            }

        });
    }

    @Override
    protected Runnable newTask(WebServer pWebServer,
                               XmlRpcStreamServer pXmlRpcServer,
                               Socket pSocket) throws IOException {
        return new ServletConnection(servlet, pSocket);
    }

    /** This exception is thrown by the request handling classes,
     * advising the server, that it should return an error response.
     */
    public static class ServletWebServerException extends IOException {

        private static final long serialVersionUID = 49879832748972394L;

        private final int statusCode;

        private final String description;

        /** Creates a new instance.
         * @param pStatusCode The HTTP status code being sent to the client.
         * @param pMessage The HTTP status message being sent to the client.
         * @param pDescription The error description being sent to the client
         * in the response body.
         */
        ServletWebServerException(int pStatusCode, String pMessage, String pDescription) {
            super(pMessage);
            statusCode = pStatusCode;
            description = pDescription;
        }

        public String getMessage() { return statusCode + " " + super.getMessage(); }

        /** Returns the error description. The server will send the description
         * as plain text in the response body.
         * @return The error description.
         */
        public String getDescription() { return description; }

        /** Returns the HTTP status code.
         * @return The status code.
         */
        public int getStatusCode() { return statusCode; }
    }

}
