package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for boolean values.
 */
public class BooleanParser extends AtomicParser {

    @Override
    protected void setResult(String pResult) throws SAXException {
        String s = pResult.trim();
        if ("1".equals(s)) {
            super.setResult(Boolean.TRUE);
        } else if ("0".equals(s)) {
            super.setResult(Boolean.FALSE);
        } else {
            throw new SAXParseException("Failed to parse boolean value: " + pResult,
                    getDocumentLocator());
        }
    }
}
