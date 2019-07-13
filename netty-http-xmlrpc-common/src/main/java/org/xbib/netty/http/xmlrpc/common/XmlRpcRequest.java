package org.xbib.netty.http.xmlrpc.common;

/**
 * Interface to an XML-RPC request made by a client.
 */
public interface XmlRpcRequest {

    /**
     * Returns the request configuration.
     * @return The request configuration.
     */
    XmlRpcRequestConfig getConfig();

    /**
     * Returns the requests method name.
     * @return Name of the method being invoked.
     */
    String getMethodName();

    /**
     * Returns the number of parameters.
     * @return Number of parameters.
     */
    int getParameterCount();

    /**
     * Returns the parameter with index <code>pIndex</code>.
     * @param pIndex Number between 0 and {@link #getParameterCount()}-1.
     * @return Parameter being sent to the server.
     */
    public Object getParameter(int pIndex);
}
