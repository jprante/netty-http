package org.xbib.netty.http.xmlrpc.common;

/**
 * This exception is thrown, if an attempt to use extensions
 * is made, but extensions aren't explicitly enabled.
 */
public class XmlRpcExtensionException extends XmlRpcException {

    private static final long serialVersionUID = 3617014169594311221L;

    /** Creates a new instance with the given error message.
     * @param pMessage The error message.
     */
    public XmlRpcExtensionException(String pMessage) {
        super(0, pMessage);
    }
}
