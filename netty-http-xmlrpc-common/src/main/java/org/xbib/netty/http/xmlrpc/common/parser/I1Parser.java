package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for byte values.
 */
public class I1Parser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(Byte.valueOf(pResult.trim()));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse byte value: " + pResult,
                    getDocumentLocator());
        }
    }
}
