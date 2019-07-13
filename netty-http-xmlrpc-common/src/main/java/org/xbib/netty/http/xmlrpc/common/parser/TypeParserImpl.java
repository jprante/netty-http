package org.xbib.netty.http.xmlrpc.common.parser;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Abstract base implementation of a {@link TypeParser},
 * for derivation of subclasses.
 */
public abstract class TypeParserImpl implements TypeParser {
    private Object result;
    private Locator locator;

    /** Sets the result object.
     * @param pResult The result object.
     */
    public void setResult(Object pResult) { result = pResult; }

    @Override
    public Object getResult() throws XmlRpcException { return result; }

    /** Returns the document locator.
     * @return Locator object describing the current location within the
     * document.
     */
    public Locator getDocumentLocator() { return locator; }
    public void setDocumentLocator(Locator pLocator) { locator = pLocator; }

    /** PI's are by default ignored.
     */
    public void processingInstruction(String pTarget, String pData) throws SAXException {
    }

    /** Skipped entities raise an exception by default.
     */
    public void skippedEntity(String pName) throws SAXException {
        throw new SAXParseException("Don't know how to handle entity " + pName,
                getDocumentLocator());
    }

    public void startPrefixMapping(String pPrefix, String pURI) throws SAXException {
    }

    public void endPrefixMapping(String pPrefix) throws SAXException {
    }

    public void endDocument() throws SAXException {
    }

    public void startDocument() throws SAXException {
    }

    protected static boolean isEmpty(char[] pChars, int pStart, int pLength) {
        for (int i = 0;  i < pLength;  i++) {
            if (!Character.isWhitespace(pChars[pStart+i])) {
                return false;
            }
        }
        return true;
    }

    public void characters(char[] pChars, int pOffset, int pLength) throws SAXException {
        if (!isEmpty(pChars, pOffset, pLength)) {
            throw new SAXParseException("Unexpected non-whitespace character data",
                    getDocumentLocator());
        }
    }

    public void ignorableWhitespace(char[] pChars, int pOffset, int pLength) throws SAXException {
    }
}
