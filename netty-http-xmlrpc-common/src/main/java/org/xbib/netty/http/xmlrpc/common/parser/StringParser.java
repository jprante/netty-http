package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;

/**
 * Parser implementation for parsing a string.
 */
public class StringParser extends AtomicParser {

	@Override
	protected void setResult(String pResult) throws SAXException {
		super.setResult((Object) pResult);
	}
}
