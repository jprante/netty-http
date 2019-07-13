package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A <code>TypeSerializer</code> is able to write a parameter
 * or result object to the XML RPC request or response.
 */
public interface TypeSerializer {

    /** Writes the object <code>pObject</code> to the SAX handler
     * <code>pHandler</code>.
     * @param pHandler The destination handler.
     * @param pObject The object being written.
     * @throws SAXException Writing the object failed.
     */
    void write(ContentHandler pHandler, Object pObject) throws SAXException;
}
