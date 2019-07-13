package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for shorts.
 */
public class I2Serializer extends TypeSerializerImpl {

    /**
     * Tag name of an i2 value.
     */
    public static final String I2_TAG = "i2";

    /**
     * Fully qualified name of an i2 value.
     */
    public static final String EX_I2_TAG = "ex:i2";

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, I2_TAG, EX_I2_TAG, pObject.toString());
    }
}
