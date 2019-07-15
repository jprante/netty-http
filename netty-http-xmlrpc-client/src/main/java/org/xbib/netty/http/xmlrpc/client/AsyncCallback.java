package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;

/**
 * A callback interface for an asynchronous XML-RPC call.
 */
public interface AsyncCallback {
    /** Call went ok, handle result.
     * @param pRequest The request being performed.
     * @param pResult The result object, which was returned by the server.
     */
    public void handleResult(XmlRpcRequest pRequest, Object pResult);

    /** Something went wrong, handle error.
     * @param pRequest The request being performed.
     * @param pError The error being thrown.
     */
    public void handleError(XmlRpcRequest pRequest, Throwable pError);
}
