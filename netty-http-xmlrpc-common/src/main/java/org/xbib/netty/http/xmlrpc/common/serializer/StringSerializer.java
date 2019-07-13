package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for strings.
 */
public class StringSerializer extends TypeSerializerImpl {

    /**
     * (Optional) Tag name of a string value.
     */
    public static final String STRING_TAG = "string";

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, null, pObject.toString());
    }
}
