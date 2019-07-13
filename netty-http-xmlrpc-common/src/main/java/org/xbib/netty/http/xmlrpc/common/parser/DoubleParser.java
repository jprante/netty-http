package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for double values.
 */
public class DoubleParser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(Double.valueOf(pResult));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse double value: " + pResult,
                    getDocumentLocator());
        }
    }
}
