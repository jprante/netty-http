package org.xbib.netty.http.xmlrpc.common.util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Utility class for working with SAX parsers.
 */
public class SAXParsers {
    private static SAXParserFactory spf;
    static {
        spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        spf.setValidating(false);
        try {
            spf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (ParserConfigurationException | SAXException e) {
            // Ignore it
        }
        try {
            spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (ParserConfigurationException | SAXException e) {
            // Ignore it
        }
    }

    /** Creates a new instance of {@link XMLReader}.
     */
    public static XMLReader newXMLReader() throws XmlRpcException {
        try {
            return spf.newSAXParser().getXMLReader();
        } catch (ParserConfigurationException | SAXException e) {
            throw new XmlRpcException("Unable to create XML parser: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the SAX parser factory, which is used by Apache XML-RPC. You may
     * use this to configure the factory.
     */
    public static SAXParserFactory getSAXParserFactory() {
        return spf;
    }

    /**
     * Sets the SAX parser factory, which is used by Apache XML-RPC. You may use
     * this to configure another instance than the default.
     */
    public static void setSAXParserFactory(SAXParserFactory pFactory) {
        spf = pFactory;
    }
}
