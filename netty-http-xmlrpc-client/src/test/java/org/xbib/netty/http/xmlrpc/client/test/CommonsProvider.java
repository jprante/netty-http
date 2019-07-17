package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcCommonsTransport;
import org.xbib.netty.http.xmlrpc.client.XmlRpcCommonsTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;

/**
 * Provider for testing the
 * {@link XmlRpcCommonsTransport}.
 */
public class CommonsProvider extends WebServerProvider {
	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 */
	public CommonsProvider(XmlRpcHandlerMapping pMapping) {
		super(pMapping, true);
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
		return new XmlRpcCommonsTransportFactory(pClient);
	}
}
