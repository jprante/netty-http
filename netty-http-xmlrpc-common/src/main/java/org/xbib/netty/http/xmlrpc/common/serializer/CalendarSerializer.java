package org.xbib.netty.http.xmlrpc.common.serializer;

import org.xbib.netty.http.xmlrpc.common.util.XsDateTimeFormat;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for date values.
 */
public class CalendarSerializer extends TypeSerializerImpl {

    private static final XsDateTimeFormat format = new XsDateTimeFormat();

    /** Tag name of a BigDecimal value.
     */
    public static final String CALENDAR_TAG = "dateTime";

    private static final String EX_CALENDAR_TAG = "ex:" + CALENDAR_TAG;

    /** Tag name of a date value.
     */
    public static final String DATE_TAG = "dateTime.iso8601";

    @Override
	public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, CALENDAR_TAG, EX_CALENDAR_TAG, format.format(pObject));
	}
}
