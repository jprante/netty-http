package org.xbib.netty.http.xmlrpc.common.parser;

import java.text.Format;
import java.text.ParseException;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for integer values.
 */
public class DateParser extends AtomicParser {
	private final Format f;

    /**
     * Creates a new instance with the given format.
     * @param pFormat format
     */
    public DateParser(Format pFormat) {
        f = pFormat;
    }

    protected void setResult(String pResult) throws SAXException {
        final String s = pResult.trim();
        if (s.length() == 0) {
            return;
        }
		try {
			super.setResult(f.parseObject(s));
		} catch (ParseException e) {
            final String msg;
            int offset = e.getErrorOffset();
            if (e.getErrorOffset() == -1) {
                msg = "Failed to parse date value: " + pResult;
            } else {
                msg = "Failed to parse date value " + pResult
                    + " at position " + offset;
            }
			throw new SAXParseException(msg, getDocumentLocator(), e);
		}
	}
}
