package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLocalTransport;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLocalTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;
import org.xbib.netty.http.xmlrpc.server.XmlRpcServer;

/**
 * Implementation of {@link BaseTest}
 * for testing the {@link XmlRpcLocalTransport}.
 */
public class LocalTransportProvider extends ClientProviderImpl {
    private XmlRpcServer server;

    /** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 */
	public LocalTransportProvider(XmlRpcHandlerMapping pMapping) {
		super(pMapping);
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
		XmlRpcLocalTransportFactory factory = new XmlRpcLocalTransportFactory(pClient);
		return factory;
	}

	public XmlRpcClientConfigImpl getConfig() throws Exception {
		XmlRpcClientConfigImpl config = super.getConfig();
		server = getXmlRpcServer();
        config.setXmlRpcServer(server);
		return config;
	}

    public XmlRpcServer getServer() {
        return server;
    }

    public void shutdown() {
        // Does nothing
    }
}
