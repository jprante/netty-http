package org.xbib.netty.http.xmlrpc.common;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * This exception is thrown by the XmlRpcClient, if an invocation of the
 * remote method failed. Failure may have two reasons: The invocation
 * failed on the remote side (for example, an exception was thrown within
 * the server) or the communication with the server failed.
 */
public class XmlRpcException extends Exception {

    private static final long serialVersionUID = 3258693217049325618L;

    /** The fault code of the exception. For servers based on this library, this
     * will always be 0. (If there are predefined error codes, they should be in
     * the XML-RPC spec.)
     */
    public final int code;

    /** If the transport was able to catch a remote exception
     * (as is the case, if the local transport is used or if extensions
     * are enabled and the server returned a serialized exception),
     * then this field contains the trapped exception.
     */
    public final Throwable linkedException;

    /** Creates a new instance with the given error code and error message.
     * @param pCode Error code.
     * @param pMessage Detail message.
     */
    public XmlRpcException(int pCode, String pMessage) {
        this(pCode, pMessage, null);
    }

    /** Creates a new instance with the given error message
     * and cause.
     * @param pMessage Detail message.
     * @param pLinkedException The errors cause.
     */
    public XmlRpcException(String pMessage, Throwable pLinkedException) {
        this(0, pMessage, pLinkedException);
    }

    /** Creates a new instance with the given error message
     * and error code 0.
     * @param pMessage Detail message.
     */
    public XmlRpcException(String pMessage) {
        this(0, pMessage, null);
    }

    /** Creates a new instance with the given error code, error message
     * and cause.
     * @param pCode Error code.
     * @param pMessage Detail message.
     * @param pLinkedException The errors cause.
     */
    public XmlRpcException(int pCode, String pMessage, Throwable pLinkedException) {
        super(pMessage);
        code = pCode;
        linkedException = pLinkedException;
    }

    @Override
    public void printStackTrace(PrintStream pStream) {
        super.printStackTrace(pStream);
        if (linkedException != null) {
            pStream.println("Caused by:");
            linkedException.printStackTrace(pStream);
        }
    }

    @Override
    public void printStackTrace(PrintWriter pWriter) {
        super.printStackTrace(pWriter);
        if (linkedException != null) {
            pWriter.println("Caused by:");
            linkedException.printStackTrace(pWriter);
        }
    }

    @Override
    public Throwable getCause() {
        return linkedException;
    }
}
