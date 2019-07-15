package org.xbib.netty.http.xmlrpc.client;

/**
 * Abstract base implementation of a factory for stream transports.
 */
public abstract class XmlRpcStreamTransportFactory extends XmlRpcTransportFactoryImpl {
	protected XmlRpcStreamTransportFactory(XmlRpcClient pClient) {
		super(pClient);
	}
}
