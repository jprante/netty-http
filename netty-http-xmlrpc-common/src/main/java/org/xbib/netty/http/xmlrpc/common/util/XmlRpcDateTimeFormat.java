package org.xbib.netty.http.xmlrpc.common.util;

import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.Calendar;
import java.util.TimeZone;

/** <p>An instance of {@link Format}, which may be used
 * to parse and format <code>dateTime</code> values, as specified
 * by the XML-RPC specification. The specification doesn't precisely
 * describe the format, it only gives an example:</p>
 * <pre>
 *   19980717T14:08:55
 * </pre>
 * This class accepts and creates instances of {@link Calendar}.
 */
public abstract class XmlRpcDateTimeFormat extends Format {
    private static final long serialVersionUID = -8008230377361175138L;

    /**
     * Returns the time zone, which is used to interpret date/time
     * values.
     * @return time zone
     */
    protected abstract TimeZone getTimeZone();

    private int parseInt(String pString, int pOffset, StringBuffer pDigits, int pMaxDigits) {
        int length = pString.length();
        pDigits.setLength(0);
        while (pMaxDigits-- > 0  &&  pOffset < length) {
            char c = pString.charAt(pOffset);
            if (Character.isDigit(c)) {
                pDigits.append(c);
                ++pOffset;
            } else {
                break;
            }
        }
        return pOffset;
    }

    public Object parseObject(String pString, ParsePosition pParsePosition) {
        if (pString == null) {
            throw new NullPointerException("The String argument must not be null.");
        }
        if (pParsePosition == null) {
            throw new NullPointerException("The ParsePosition argument must not be null.");
        }
        int offset = pParsePosition.getIndex();
        int length = pString.length();

        StringBuffer digits = new StringBuffer();
        int year, month, mday;

        offset = parseInt(pString, offset, digits, 4);
        if (digits.length() < 4) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        year = Integer.parseInt(digits.toString());
	
        offset = parseInt(pString, offset, digits, 2);
        if (digits.length() != 2) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        month = Integer.parseInt(digits.toString());
	
        offset = parseInt(pString, offset, digits, 2);
        if (digits.length() != 2) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        mday = Integer.parseInt(digits.toString());

        if (offset < length  &&  pString.charAt(offset) == 'T') {
            ++offset;
        } else {
            pParsePosition.setErrorIndex(offset);
            return null;
        }

        int hour, minute, second;
        offset = parseInt(pString, offset, digits, 2);
        if (digits.length() != 2) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        hour = Integer.parseInt(digits.toString());
	
        if (offset < length  &&  pString.charAt(offset) == ':') {
            ++offset;
        } else {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
	
        offset = parseInt(pString, offset, digits, 2);
        if (digits.length() != 2) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        minute = Integer.parseInt(digits.toString());
	
        if (offset < length  &&  pString.charAt(offset) == ':') {
            ++offset;
        } else {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
	
        offset = parseInt(pString, offset, digits, 2);
        if (digits.length() != 2) {
            pParsePosition.setErrorIndex(offset);
            return null;
        }
        second = Integer.parseInt(digits.toString());
	
        Calendar cal = Calendar.getInstance(getTimeZone());
        cal.set(year, month-1, mday, hour, minute, second);
        cal.set(Calendar.MILLISECOND, 0);
        pParsePosition.setIndex(offset);
        return cal;
    }

    private void append(StringBuffer pBuffer, int pNum, int pMinLen) {
        String s = Integer.toString(pNum);
        for (int i = s.length();  i < pMinLen;  i++) {
            pBuffer.append('0');
        }
        pBuffer.append(s);
    }

    public StringBuffer format(Object pCalendar, StringBuffer pBuffer, FieldPosition pPos) {
        if (pCalendar == null) {
            throw new NullPointerException("The Calendar argument must not be null.");
        }
        if (pBuffer == null) {
            throw new NullPointerException("The StringBuffer argument must not be null.");
        }
        if (pPos == null) {
            throw new NullPointerException("The FieldPosition argument must not be null.");
        }

        Calendar cal = (Calendar) pCalendar;
        int year = cal.get(Calendar.YEAR);
        append(pBuffer, year, 4);
        append(pBuffer, cal.get(Calendar.MONTH)+1, 2);
        append(pBuffer, cal.get(Calendar.DAY_OF_MONTH), 2);
        pBuffer.append('T');
        append(pBuffer, cal.get(Calendar.HOUR_OF_DAY), 2);
        pBuffer.append(':');
        append(pBuffer, cal.get(Calendar.MINUTE), 2);
        pBuffer.append(':');
        append(pBuffer, cal.get(Calendar.SECOND), 2);
        return pBuffer;
    }
}
