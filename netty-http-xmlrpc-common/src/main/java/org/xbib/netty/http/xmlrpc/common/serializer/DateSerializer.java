package org.xbib.netty.http.xmlrpc.common.serializer;

import java.text.Format;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * A {@link TypeSerializer} for date values.
 */
public class DateSerializer extends TypeSerializerImpl {

    /** Tag name of a date value.
     */
    public static final String DATE_TAG = "dateTime.iso8601";

    private final Format format;

    /**
     * Creates a new instance with the given formatter.
     * @param pFormat format
     */
    public DateSerializer(Format pFormat) {
        format = pFormat;
    }

    @Override
	public void write(ContentHandler pHandler, Object pObject) throws SAXException {
        write(pHandler, DATE_TAG, format.format(pObject));
	}
}
