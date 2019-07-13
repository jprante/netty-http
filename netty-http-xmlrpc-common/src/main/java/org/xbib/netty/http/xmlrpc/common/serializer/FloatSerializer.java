package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *  A {@link TypeSerializer} for floats.
 */
public class FloatSerializer extends TypeSerializerImpl {
    /** Tag name of a float value.
     */
    public static final String FLOAT_TAG = "float";

    /** Fully qualified name of a float value.
     */
    public static final String EX_FLOAT_TAG = "ex:float";

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, FLOAT_TAG, EX_FLOAT_TAG, pObject.toString());
    }
}
