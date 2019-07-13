package org.xbib.netty.http.xmlrpc.common.parser;

import java.util.Base64;

import javax.xml.namespace.QName;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A parser for base64 elements.
 */
public class ByteArrayParser extends TypeParserImpl {
    private int level;
    private StringBuilder sb;

    @Override
    public void startDocument() throws SAXException {
        level = 0;
    }

    @Override
    public void characters(char[] pChars, int pStart, int pLength) throws SAXException {
        sb.append(new String(pChars, pStart, pLength));
    }

    @Override
    public void endElement(String pURI, String pLocalName, String pQName) throws SAXException {
        if (--level == 0) {
            setResult(Base64.getDecoder().decode(sb.toString()));
        } else {
            throw new SAXParseException("Unexpected end tag in atomic element: "
                    + new QName(pURI, pLocalName),
                    getDocumentLocator());
        }
    }

    @Override
    public void startElement(String pURI, String pLocalName, String pQName, Attributes pAttrs) throws SAXException {
        if (level++ == 0) {
            sb = new StringBuilder();
        } else {
            throw new SAXParseException("Unexpected start tag in atomic element: "
                    + new QName(pURI, pLocalName),
                    getDocumentLocator());
        }
    }
}
