package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for float values.
 */
public class FloatParser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(Float.valueOf(pResult));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse float value: " + pResult,
                    getDocumentLocator());
        }
    }
}
