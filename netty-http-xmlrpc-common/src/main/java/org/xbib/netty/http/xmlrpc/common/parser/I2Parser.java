package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for short values.
 */
public class I2Parser extends AtomicParser {

	@Override
	protected void setResult(String pResult) throws SAXException {
		try {
			super.setResult(Short.valueOf(pResult.trim()));
		} catch (NumberFormatException e) {
			throw new SAXParseException("Failed to parse short value: " + pResult,
										getDocumentLocator());
		}
	}
}
