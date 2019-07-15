package org.xbib.netty.http.xmlrpc.client;


import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

/** <p>This is thrown by many of the client classes if an error occured processing
 * and XML-RPC request or response due to client side processing..</p>
 */
public class XmlRpcClientException extends XmlRpcException {
	private static final long serialVersionUID = 3545798797134608691L;

	/**
     * Create an XmlRpcClientException with the given message and
     * underlying cause exception.
     *
     * @param pMessage the message for this exception.
     * @param pCause the cause of the exception.
     */
    public XmlRpcClientException(String pMessage, Throwable pCause) {
        super(0, pMessage, pCause);
    }
}
