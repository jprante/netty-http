package org.xbib.netty.http.xmlrpc.client;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;

/**
 * <p>Interface from XML-RPC to an underlying transport, most likely based on HTTP.</p>
 */
public interface XmlRpcTransport {

    /**  Send an XML-RPC message. This method is called to send a message to the
     * other party.
     * @param pRequest The request being performed.
     * @return Result object, if invoking the remote method was successfull.
     * @throws XmlRpcException Performing the request failed.
     */
    Object sendRequest(XmlRpcRequest pRequest) throws XmlRpcException;
}
