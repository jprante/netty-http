package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.TypeConverterFactory;
import org.xbib.netty.http.xmlrpc.common.TypeConverterFactoryImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcController;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestProcessor;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorker;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorkerFactory;

/** A multithreaded, reusable XML-RPC server object. The name may
 * be misleading because this does not open any server sockets.
 * Instead it is fed by passing instances of
 * {@link XmlRpcRequest} from
 * a transport.
 */
public class XmlRpcServer extends XmlRpcController
		implements XmlRpcRequestProcessor {
	private XmlRpcHandlerMapping handlerMapping;
    private TypeConverterFactory typeConverterFactory = new TypeConverterFactoryImpl();
	private XmlRpcServerConfig config = new XmlRpcServerConfigImpl();

	protected XmlRpcWorkerFactory getDefaultXmlRpcWorkerFactory() {
		return new XmlRpcServerWorkerFactory(this);
	}

    /** Sets the servers {@link TypeConverterFactory}.
     */
    public void setTypeConverterFactory(TypeConverterFactory pFactory) {
        typeConverterFactory = pFactory;
    }
    public TypeConverterFactory getTypeConverterFactory() {
        return typeConverterFactory;
    }

	/** Sets the servers configuration.
	 * @param pConfig The new server configuration.
	 */
	public void setConfig(XmlRpcServerConfig pConfig) { config = pConfig; }
	public XmlRpcConfig getConfig() { return config; }

	/** Sets the servers handler mapping.
	 * @param pMapping The servers handler mapping.
	 */
	public void setHandlerMapping(XmlRpcHandlerMapping pMapping) {
		handlerMapping = pMapping;
	}

	/** Returns the servers handler mapping.
	 * @return The servers handler mapping.
	 */
	public XmlRpcHandlerMapping getHandlerMapping() {
		return handlerMapping;
	}

	/** Performs the given request.
	 * @param pRequest The request being executed.
	 * @return The result object.
	 * @throws XmlRpcException The request failed.
	 */
	public Object execute(XmlRpcRequest pRequest) throws XmlRpcException {
	    final XmlRpcWorkerFactory factory = getWorkerFactory();
	    final XmlRpcWorker worker = factory.getWorker();
        try {
            return worker.execute(pRequest);
        } finally {
            factory.releaseWorker(worker);
        }
	}
}
