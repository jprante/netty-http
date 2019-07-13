package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Abstract base implementation of a type serializer.
 */
public abstract class TypeSerializerImpl implements TypeSerializer {
    protected static final Attributes ZERO_ATTRIBUTES = new AttributesImpl();
    /** Tag name of a value element.
     */
    public static final String VALUE_TAG = "value";

    protected void write(ContentHandler pHandler, String pTagName, String pValue) throws SAXException {
        write(pHandler, pTagName, pValue.toCharArray());
    }

    protected void write(ContentHandler pHandler, String pTagName, char[] pValue) throws SAXException {
        pHandler.startElement("", TypeSerializerImpl.VALUE_TAG, TypeSerializerImpl.VALUE_TAG, ZERO_ATTRIBUTES);
        if (pTagName != null) {
            pHandler.startElement("", pTagName, pTagName, ZERO_ATTRIBUTES);
        }
        pHandler.characters(pValue, 0, pValue.length);
        if (pTagName != null) {
            pHandler.endElement("", pTagName, pTagName);
        }
        pHandler.endElement("", TypeSerializerImpl.VALUE_TAG, TypeSerializerImpl.VALUE_TAG);
    }

    protected void write(ContentHandler pHandler, String pLocalName, String pQName,
                         String pValue) throws SAXException {
        pHandler.startElement("", TypeSerializerImpl.VALUE_TAG, TypeSerializerImpl.VALUE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement(XmlRpcWriter.EXTENSIONS_URI, pLocalName, pQName, ZERO_ATTRIBUTES);
        char[] value = pValue.toCharArray();
        pHandler.characters(value, 0, value.length);
        pHandler.endElement(XmlRpcWriter.EXTENSIONS_URI, pLocalName, pQName);
        pHandler.endElement("", TypeSerializerImpl.VALUE_TAG, TypeSerializerImpl.VALUE_TAG);
    }
}
