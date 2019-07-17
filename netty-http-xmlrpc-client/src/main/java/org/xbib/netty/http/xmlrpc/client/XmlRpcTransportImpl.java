package org.xbib.netty.http.xmlrpc.client;

/**
 * Abstract base implementation of an {@link XmlRpcTransport}.
 */
public abstract class XmlRpcTransportImpl implements XmlRpcTransport {
	private final XmlRpcClient client;

	/** Creates a new instance.
	 * @param pClient The client, which creates the transport.
	 */
	protected XmlRpcTransportImpl(XmlRpcClient pClient) {
		client = pClient;
	}

	/** Returns the client, which created this transport.
	 * @return The client.
	 */
	public XmlRpcClient getClient() { return client; }
}
