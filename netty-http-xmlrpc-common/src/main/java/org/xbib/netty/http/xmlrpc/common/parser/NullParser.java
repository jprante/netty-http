package org.xbib.netty.http.xmlrpc.common.parser;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * SAX parser for a nil element (null value).
 */
public class NullParser extends AtomicParser {
	protected void setResult(String pResult) throws SAXException {
		if (pResult == null  ||  "".equals(pResult.trim())) {
			super.setResult((Object) null);
		} else {
			throw new SAXParseException("Unexpected characters in nil element.",
										getDocumentLocator());
		}
	}
}
