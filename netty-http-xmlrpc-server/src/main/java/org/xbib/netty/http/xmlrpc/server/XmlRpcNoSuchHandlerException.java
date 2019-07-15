package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;

/**
 * This exception is thrown, if an unknown handler is called.
 */
public class XmlRpcNoSuchHandlerException extends XmlRpcException {
    private static final long serialVersionUID = 3257002138218344501L;

    /** Creates a new instance with the given message.
     * @param pMessage The error details.
     */
    public XmlRpcNoSuchHandlerException(String pMessage) {
        super(0, pMessage);
    }
}
