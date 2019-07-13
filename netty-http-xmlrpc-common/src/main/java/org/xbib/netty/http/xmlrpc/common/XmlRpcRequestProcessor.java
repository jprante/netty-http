package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface of an object, which is able to process
 * XML-RPC requests.
 */
public interface XmlRpcRequestProcessor {

    /**
     * Processes the given request and returns a
     * result object.
     * @param pRequest request
     * @return result
     * @throws XmlRpcException Processing the request failed.
     */
    Object execute(XmlRpcRequest pRequest) throws XmlRpcException;

    /**
     * Returns the request processors {@link TypeConverterFactory}.
     * @return type converter factory
     */
    TypeConverterFactory getTypeConverterFactory();
}
