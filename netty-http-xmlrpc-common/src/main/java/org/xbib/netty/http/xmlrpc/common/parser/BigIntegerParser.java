package org.xbib.netty.http.xmlrpc.common.parser;

import java.math.BigInteger;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for BigInteger values.
 */
public class BigIntegerParser extends AtomicParser {
    protected void setResult(String pResult) throws SAXException {
        try {
            super.setResult(new BigInteger(pResult));
        } catch (NumberFormatException e) {
            throw new SAXParseException("Failed to parse BigInteger value: " + pResult,
                                        getDocumentLocator());
        }
    }
}
