package org.xbib.netty.http.xmlrpc.common.parser;

import javax.xml.namespace.QName;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Abstract base implementation of {@link TypeParser}
 * for parsing an atomic value.
 */
public abstract class AtomicParser extends TypeParserImpl {
    private int level;
    protected StringBuffer sb;

    /**
     * Creates a new instance.
     */
    protected AtomicParser() {
    }

    protected abstract void setResult(String pResult) throws SAXException;

    public void startDocument() throws SAXException {
        level = 0;
    }

    public void characters(char[] pChars, int pStart, int pLength) throws SAXException {
        if (sb == null) {
            if (!isEmpty(pChars, pStart, pLength)) {
                throw new SAXParseException("Unexpected non-whitespace characters",
                        getDocumentLocator());
            }
        } else {
            sb.append(pChars, pStart, pLength);
        }
    }

    public void endElement(String pURI, String pLocalName, String pQName) throws SAXException {
        if (--level == 0) {
            setResult(sb.toString());
        } else {
            throw new SAXParseException("Unexpected end tag in atomic element: "
                    + new QName(pURI, pLocalName),
                    getDocumentLocator());
        }
    }

    public void startElement(String pURI, String pLocalName, String pQName, Attributes pAttrs) throws SAXException {
        if (level++ == 0) {
            sb = new StringBuffer();
        } else {
            throw new SAXParseException("Unexpected start tag in atomic element: "
                    + new QName(pURI, pLocalName),
                    getDocumentLocator());
        }
    }
}
