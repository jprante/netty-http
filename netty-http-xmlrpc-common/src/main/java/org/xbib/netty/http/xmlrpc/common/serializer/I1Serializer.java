package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for bytes.
 */
public class I1Serializer extends TypeSerializerImpl {

    /**
     * Tag name of an i1 value.
     */
    public static final String I1_TAG = "i1";

    /**
     * Fully qualified name of an i1 value.
     */
    public static final String EX_I1_TAG = "ex:i1";

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, I1_TAG, EX_I1_TAG, pObject.toString());
    }
}
