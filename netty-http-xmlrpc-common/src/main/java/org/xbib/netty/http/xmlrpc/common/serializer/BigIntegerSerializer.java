package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for BigInteger.
 */
public class BigIntegerSerializer extends TypeSerializerImpl {
    /** Tag name of a BigDecimal value.
     */
    public static final String BIGINTEGER_TAG = "biginteger";

    private static final String EX_BIGINTEGER_TAG = "ex:" + BIGINTEGER_TAG;

    @Override
    public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, BIGINTEGER_TAG, EX_BIGINTEGER_TAG, pObject.toString());
    }
}
