package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLocalStreamTransport;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLocalStreamTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcLocalStreamServer;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;

/** Implementation of {@link BaseTest}
 * for testing the {@link XmlRpcLocalStreamTransport}.
 */
public class LocalStreamTransportProvider extends LocalTransportProvider {
    private XmlRpcLocalStreamServer server;

    /** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 */
	public LocalStreamTransportProvider(XmlRpcHandlerMapping pMapping) {
		super(pMapping);
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
	    server = new XmlRpcLocalStreamServer();
        XmlRpcLocalStreamTransportFactory factory
			= new XmlRpcLocalStreamTransportFactory(pClient, server);
		return factory;
	}

    public XmlRpcServer getServer() {
        return server;
    }
}
