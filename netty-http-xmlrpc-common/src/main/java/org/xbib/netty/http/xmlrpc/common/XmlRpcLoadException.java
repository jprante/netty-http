package org.xbib.netty.http.xmlrpc.common;

/**
 * This exception is thrown, if the clients or servers maximum
 * number of concurrent threads is exceeded.
 */
public class XmlRpcLoadException extends XmlRpcException {

    private static final long serialVersionUID = 4050760511635272755L;

    /** Creates a new instance.
     * @param pMessage Error description.
     */
    public XmlRpcLoadException(String pMessage) {
        super(0, pMessage, null);
    }
}
