package org.xbib.netty.http.xmlrpc.common;

/**
 * An instance of {@link XmlRpcRequestProcessor},
 * which is processing an XML stream.
 */
public interface XmlRpcStreamRequestProcessor extends XmlRpcRequestProcessor {

    /**
     * Reads an XML-RPC request from the connection
     * object and processes the request, writing the
     * result to the same connection object.
     * @param pConfig config
     * @param pConnection connection
     * @throws XmlRpcException Processing the request failed.
     */
    void execute(XmlRpcStreamRequestConfig pConfig, ServerStreamConnection pConnection) throws XmlRpcException;
}
