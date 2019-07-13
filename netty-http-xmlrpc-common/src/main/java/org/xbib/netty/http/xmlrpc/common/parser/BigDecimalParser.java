package org.xbib.netty.http.xmlrpc.common.parser;

import java.math.BigDecimal;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for BigDecimal values.
 */
public class BigDecimalParser extends AtomicParser {
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(new BigDecimal(pResult));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse BigDecimal value: " + pResult,
                                        getDocumentLocator());
        }
    }
}
