package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for long values.
 */
public class I8Parser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(Long.valueOf(pResult.trim()));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse long value: " + pResult,
                    getDocumentLocator());
        }
    }
}
