package org.xbib.netty.http.xmlrpc.common;

/**
 * This exception is thrown, if the server catches an exception, which
 * is thrown by the handler.
 */
public class XmlRpcInvocationException extends XmlRpcException {

    private static final long serialVersionUID = 7439737967784966169L;

    /**
     * Creates a new instance with the given error code, error message
     * and cause.
     * @param pCode code
     * @param pMessage message
     * @param pLinkedException exception
     */
    public XmlRpcInvocationException(int pCode, String pMessage, Throwable pLinkedException) {
        super(pCode, pMessage, pLinkedException);
    }

    /**
     * Creates a new instance with the given error message and cause.
     * @param pMessage message
     * @param pLinkedException exception
     */
    public XmlRpcInvocationException(String pMessage, Throwable pLinkedException) {
        super(pMessage, pLinkedException);
    }
}
