package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for integer values.
 */
public class I4Parser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(Integer.valueOf(pResult.trim()));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse integer value: " + pResult,
                    getDocumentLocator());
        }
    }
}
