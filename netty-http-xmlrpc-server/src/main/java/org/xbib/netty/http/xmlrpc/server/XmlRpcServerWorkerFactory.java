package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcWorker;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorkerFactory;

/** Server specific worker factory.
 */
public class XmlRpcServerWorkerFactory extends XmlRpcWorkerFactory {
	/** Creates a new factory with the given controller.
	 * @param pServer The factory controller.
	 */
	public XmlRpcServerWorkerFactory(XmlRpcServer pServer) {
		super(pServer);
	}

	protected XmlRpcWorker newWorker() {
		return new XmlRpcServerWorker(this);
	}
}
