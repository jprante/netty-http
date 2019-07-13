package org.xbib.netty.http.xmlrpc.common;

/**
 * An object, which executes requests on the controllers
 * behalf. These objects are mainly used for controlling the
 * clients or servers load, which is defined in terms of the
 * number of currently active workers.
 */
public interface XmlRpcWorker {

	/**
	 * Returns the workers controller.
	 * @return The controller
	 */
	XmlRpcController getController();

	/**
	 * Performs a synchronous request. The client worker extends
	 * this interface with the ability to perform asynchronous
	 * requests.
	 * @param pRequest The request being performed.
	 * @return The requests result.
	 * @throws XmlRpcException Performing the request failed.
	 */
	Object execute(XmlRpcRequest pRequest) throws XmlRpcException;
}
