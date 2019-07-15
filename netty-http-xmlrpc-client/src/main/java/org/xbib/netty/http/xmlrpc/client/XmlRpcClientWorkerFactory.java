package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcWorker;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorkerFactory;

/**
 * A worker factory for the client, creating instances of
 * {@link XmlRpcClientWorker}.
 */
public class XmlRpcClientWorkerFactory extends XmlRpcWorkerFactory {
	/** Creates a new instance.
	 * @param pClient The factory controller.
	 */
	public XmlRpcClientWorkerFactory(XmlRpcClient pClient) {
		super(pClient);
	}

	/** Creates a new worker instance.
	 * @return New instance of {@link XmlRpcClientWorker}.
	 */
	protected XmlRpcWorker newWorker() {
		return new XmlRpcClientWorker(this);
	}
}
