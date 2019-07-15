package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcController;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHandler;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorker;

/** Server specific implementation of {@link XmlRpcWorker}.
 */
public class XmlRpcServerWorker implements XmlRpcWorker {
	private final XmlRpcServerWorkerFactory factory;

	/** Creates a new instance.
	 * @param pFactory The factory creating the worker.
	 */
	public XmlRpcServerWorker(XmlRpcServerWorkerFactory pFactory) {
		factory = pFactory;
	}

	public XmlRpcController getController() { return factory.getController(); }

	public Object execute(XmlRpcRequest pRequest) throws XmlRpcException {
		XmlRpcServer server = (XmlRpcServer) getController();
		XmlRpcHandlerMapping mapping = server.getHandlerMapping();
		XmlRpcHandler handler = mapping.getHandler(pRequest.getMethodName());
		return handler.execute(pRequest);
	}
}
