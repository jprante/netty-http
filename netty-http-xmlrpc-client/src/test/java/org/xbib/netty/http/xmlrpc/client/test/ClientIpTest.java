package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHandler;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcNoSuchHandlerException;
import org.xbib.netty.http.xmlrpc.servlet.XmlRpcServlet;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Test case for reading the clients IP address.
 */
public class ClientIpTest extends XmlRpcTestCase {
    /**
     * An object, which provides additional information
     * about the client to the user.
     */
    public static class ClientInfo {
        private final String ipAddress;

        /**
         * Creates a new instance.
         */
        public ClientInfo(String pIpAddress) {
            ipAddress = pIpAddress;
        }

        /**
         * Returns the clients IP address.
         */
        public String getIpAddress() {
            return ipAddress;
        }
    }

    /**
     * An extension of the {@link XmlRpcServlet}, which
     * ensures the availability of a {@link ClientInfo}
     * object.
     */
    public static class ClientInfoServlet extends XmlRpcServlet {

        private static final long serialVersionUID = 8210342625908021538L;

        private static final ThreadLocal<ClientInfo> clientInfo = new ThreadLocal<>();

        /**
         * Returns the current threads. client info object.
         */
        static ClientInfo getClientInfo() {
            return clientInfo.get();
        }

        public void doPost(HttpServletRequest pRequest,
                HttpServletResponse pResponse) throws IOException,
                ServletException {
            clientInfo.set(new ClientInfo(pRequest.getRemoteAddr()));
            super.doPost(pRequest, pResponse);
        }
    }

    private static class ClientIpTestProvider extends ServletWebServerProvider {
        ClientIpTestProvider(XmlRpcHandlerMapping pMapping, boolean pContentLength)
                throws ServletException, IOException {
            super(pMapping, pContentLength);
        }

        protected XmlRpcServlet newXmlRpcServlet() {
            return new ClientInfoServlet();
        }
    }
    
    protected ClientProvider[] initProviders(XmlRpcHandlerMapping pMapping)
            throws ServletException, IOException {
        return new ClientProvider[]{
            new ClientIpTestProvider(pMapping, false),
            new ClientIpTestProvider(pMapping, true)
        };
    }

    protected XmlRpcHandlerMapping getHandlerMapping() {
        final XmlRpcHandler handler = pRequest -> {
            final ClientInfo clientInfo = ClientInfoServlet.getClientInfo();
            if (clientInfo == null) {
                return "";
            }
            final String ip = clientInfo.getIpAddress();
            if (ip == null) {
                return "";
            }
            return ip;
        };
        return new XmlRpcHandlerMapping(){
            public XmlRpcHandler getHandler(String pHandlerName)
                    throws XmlRpcNoSuchHandlerException, XmlRpcException {
                return handler;
            }
        };
    }

    private void testClientIpAddress(ClientProvider pProvider) throws Exception {
        final XmlRpcClient client = pProvider.getClient();
        client.setConfig(getConfig(pProvider));
        final String ip = (String) client.execute("getIpAddress", new Object[]{});
        assertEquals("127.0.0.1", ip);
    }
    
    /** Test, whether we can invoke a method, returning a byte.
     * @throws Exception The test failed.
     */
    public void testClientIpAddress() throws Exception {
        for (int i = 0;  i < providers.length;  i++) {
            testClientIpAddress(providers[i]);
        }
    }
}
