package org.xbib.netty.http.xmlrpc.client;

/** Factory for the lite HTTP transport,
 * {@link XmlRpcLiteHttpTransport}.
 */
public class XmlRpcLiteHttpTransportFactory extends XmlRpcTransportFactoryImpl {
	/**
	 * Creates a new instance.
	 * @param pClient The client, which will invoke the factory.
	 */
	public XmlRpcLiteHttpTransportFactory(XmlRpcClient pClient) {
		super(pClient);
	}

	public XmlRpcTransport getTransport() { return new XmlRpcLiteHttpTransport(getClient()); }
}
