package org.xbib.netty.http.xmlrpc.common.util;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

import org.xml.sax.SAXException;

/**
 * An improved version of {@link XMLWriterImpl},
 * using {@link Charset}.
 */
public class CharSetXMLWriter extends XMLWriterImpl {

    private CharsetEncoder charsetEncoder;

    @Override
    public void startDocument() throws SAXException {
        Charset charSet = Charset.forName(getEncoding());
        if (charSet.canEncode()) {
            charsetEncoder = charSet.newEncoder();
        }
    }

    @Override
    public boolean canEncode(char c) {
        return (charsetEncoder != null) && charsetEncoder.canEncode(c);
    }
}
