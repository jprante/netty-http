package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for BigDecimal.
 */
public class BigDecimalSerializer extends TypeSerializerImpl {
    /**
     * Tag name of a BigDecimal value.
     */
    public static final String BIGDECIMAL_TAG = "bigdecimal";

    private static final String EX_BIGDECIMAL_TAG = "ex:" + BIGDECIMAL_TAG;

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, BIGDECIMAL_TAG, EX_BIGDECIMAL_TAG, pObject.toString());
    }
}
