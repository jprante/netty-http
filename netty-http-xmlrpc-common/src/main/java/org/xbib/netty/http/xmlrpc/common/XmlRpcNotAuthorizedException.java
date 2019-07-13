package org.xbib.netty.http.xmlrpc.common;

/**
 * This exception must be thrown, if the user isn't authenticated.
 */
public class XmlRpcNotAuthorizedException extends XmlRpcException {

    private static final long serialVersionUID = 3258410629709574201L;

    /** Creates a new instance with the given error message.
     * @param pMessage The error message.
     */
    public XmlRpcNotAuthorizedException(String pMessage) {
        super(0, pMessage);
    }
}
