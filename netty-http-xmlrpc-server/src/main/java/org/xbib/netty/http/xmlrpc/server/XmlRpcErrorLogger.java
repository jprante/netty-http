package org.xbib.netty.http.xmlrpc.server;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Instances of this class can be used to customize the servers
 * error logging.
 */
public class XmlRpcErrorLogger {
    private static final Logger log = Logger.getLogger(XmlRpcErrorLogger.class.getName());

    /**
     * Called to log the given error.
     */
    public void log(String pMessage, Throwable pThrowable) {
        log.log(Level.SEVERE, pMessage, pThrowable);
    }

    /**
     * Called to log the given error message.
     */
    public void log(String pMessage) {
        log.log(Level.SEVERE, pMessage);
    }
}
