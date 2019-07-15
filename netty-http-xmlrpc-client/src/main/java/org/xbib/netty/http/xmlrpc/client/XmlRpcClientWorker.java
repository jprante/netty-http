package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcController;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcWorker;

/** Object, which performs a request on the clients behalf.
 * The client maintains a pool of workers. The main purpose of the
 * pool is limitation of the maximum number of concurrent requests.
 */
public class XmlRpcClientWorker implements XmlRpcWorker {
	private final XmlRpcClientWorkerFactory factory;

	/** Creates a new instance.
	 * @param pFactory The factory, which is being notified, if
	 * the worker's ready.
	 */
	public XmlRpcClientWorker(XmlRpcClientWorkerFactory pFactory) {
		factory = pFactory;
	}

	public XmlRpcController getController() {
		return factory.getController();
	}

	/** Performs a synchronous request.
	 * @param pRequest The request being performed.
	 * @return The requests result.
	 * @throws XmlRpcException Performing the request failed.
	 */
	public Object execute(XmlRpcRequest pRequest)
			throws XmlRpcException {
		try {
			XmlRpcClient client = (XmlRpcClient) getController();
			return client.getTransportFactory().getTransport().sendRequest(pRequest);
		} finally {
			factory.releaseWorker(this);
		}
	}

	protected Thread newThread(Runnable pRunnable) {
		Thread result = new Thread(pRunnable);
		result.setDaemon(true);
		return result;
	}

	/** Performs an synchronous request.
	 * @param pRequest The request being performed.
	 * @param pCallback The callback being invoked, when the request is finished.
	 */
	public void execute(final XmlRpcRequest pRequest,
						final AsyncCallback pCallback) {
		Runnable runnable = new Runnable(){
			public void run(){
				Object result = null;
				Throwable th = null;
				try {
					XmlRpcClient client = (XmlRpcClient) getController();
					result = client.getTransportFactory().getTransport().sendRequest(pRequest);
				} catch (Throwable t) {
					th = t;
				}
				factory.releaseWorker(XmlRpcClientWorker.this);
				if (th == null) {
					pCallback.handleResult(pRequest, result);
				} else {
					pCallback.handleError(pRequest, th);
				}
			}
		};
		newThread(runnable).start();
	}
}
