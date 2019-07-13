package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for doubles.
 */
public class DoubleSerializer extends TypeSerializerImpl {

    /**
     * Tag name of a double value.
     */
    public static final String DOUBLE_TAG = "double";

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, DOUBLE_TAG, pObject.toString());
    }
}
