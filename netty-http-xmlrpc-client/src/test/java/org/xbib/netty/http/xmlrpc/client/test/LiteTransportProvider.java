package org.xbib.netty.http.xmlrpc.client.test;

import org.xbib.netty.http.xmlrpc.client.XmlRpcClient;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLiteHttpTransport;
import org.xbib.netty.http.xmlrpc.client.XmlRpcLiteHttpTransportFactory;
import org.xbib.netty.http.xmlrpc.client.XmlRpcTransportFactory;
import org.xbib.netty.http.xmlrpc.server.XmlRpcHandlerMapping;

/** Provider for testing the
 * {@link XmlRpcLiteHttpTransport}.
 */
public class LiteTransportProvider extends WebServerProvider {
	/** Creates a new instance.
	 * @param pMapping The test servers handler mapping.
	 * @param pContentLength Whether a Content-Length header is required.
	 */
	public LiteTransportProvider(XmlRpcHandlerMapping pMapping,
								 boolean pContentLength) {
		super(pMapping, pContentLength);
	}

	protected XmlRpcTransportFactory getTransportFactory(XmlRpcClient pClient) {
		return new XmlRpcLiteHttpTransportFactory(pClient);
	}
}
