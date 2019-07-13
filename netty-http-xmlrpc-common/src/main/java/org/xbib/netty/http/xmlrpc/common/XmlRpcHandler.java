package org.xbib.netty.http.xmlrpc.common;

/**
 * The XML-RPC server uses this interface to call a method of an RPC handler.
 */
public interface XmlRpcHandler {

    /**
     *  Performs the request and returns the result object.
     * @param pRequest The request being performed (method name and
     * parameters.)
     * @return The result object.
     * @throws XmlRpcException Performing the request failed.
     */
    Object execute(XmlRpcRequest pRequest) throws XmlRpcException;
}
