package org.xbib.netty.http.xmlrpc.common.parser;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xml.sax.ContentHandler;

/**
 * Interface of a SAX handler parsing a single parameter or
 * result object.
 */
public interface TypeParser extends ContentHandler {

    /**
     * Returns the parsed object.
     * @return The parameter or result object.
     * @throws XmlRpcException Creating the result object failed.
     * @throws IllegalStateException The method was invoked before
     * {@link ContentHandler#endDocument}.
     */
    Object getResult() throws XmlRpcException;
}
