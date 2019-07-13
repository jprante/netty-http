package org.xbib.netty.http.xmlrpc.common.parser;

import java.text.ParseException;

import org.xbib.netty.http.xmlrpc.common.util.XsDateTimeFormat;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Parser for integer values.
 */
public class CalendarParser extends AtomicParser {

    private static final XsDateTimeFormat format = new XsDateTimeFormat();

    @Override
    protected void setResult(String pResult) throws SAXException {
		try {
			super.setResult(format.parseObject(pResult.trim()));
		} catch (ParseException e) {
            int offset = e.getErrorOffset();
            final String msg;
            if (offset == -1) {
                msg = "Failed to parse dateTime value: " + pResult;
            } else {
                msg = "Failed to parse dateTime value " + pResult
                    + " at position " + e.getErrorOffset();
            }
            throw new SAXParseException(msg, getDocumentLocator(), e);
		}
	}
}
