package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcSunHttpTransport;
import org.xbib.netty.http.xmlrpc.client.XmlRpcSunHttpTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;

/** Implementation of {@link BaseTest} for testing the
 * {@link XmlRpcSunHttpTransport}.
 */
public class SunHttpTransportProvider extends WebServerProvider {
	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 * @param pContentLength Number of bytes being transmitted.
	 */
	public SunHttpTransportProvider(XmlRpcHandlerMapping pMapping, boolean pContentLength) {
		super(pMapping, pContentLength);
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
		return new XmlRpcSunHttpTransportFactory(pClient);
	}
}
