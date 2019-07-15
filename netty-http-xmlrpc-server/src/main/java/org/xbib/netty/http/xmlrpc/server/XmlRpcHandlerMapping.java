package org.xbib.netty.http.xmlrpc.server;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcHandler;

/** Maps from a handler name to a handler object.
 */
public interface XmlRpcHandlerMapping {
    /**
     * Return the handler for the specified handler name.
     * @param handlerName The name of the handler to retrieve.
     * @return Object The desired handler. Never null, an exception
     * is thrown if no such handler is available.
     * @throws XmlRpcNoSuchHandlerException The handler is not available.
     * @throws XmlRpcException An internal error occurred.
     */
    XmlRpcHandler getHandler(String handlerName)
            throws XmlRpcNoSuchHandlerException, XmlRpcException;
}
