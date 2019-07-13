package org.xbib.netty.http.xmlrpc.common.parser;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Map;

import javax.xml.namespace.QName;

import org.xbib.netty.http.xmlrpc.common.TypeFactory;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.util.NamespaceContextImpl;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * A SAX parser for an server response.
 */
public class XmlRpcResponseParser extends RecursiveTypeParserImpl {

    private int level;

    private boolean isSuccess;

    private int errorCode;

    private String errorMessage;

    private Throwable errorCause;

    /** Creates a new instance.
     * @param pConfig The response configuration.
     * @param pTypeFactory The type factory for creating instances of
     * {@link TypeParser}.
     */
    public XmlRpcResponseParser(XmlRpcStreamRequestConfig pConfig,
                                TypeFactory pTypeFactory) {
        super(pConfig, new NamespaceContextImpl(), pTypeFactory);
    }

    @SuppressWarnings("unchecked")
    protected void addResult(Object pResult) throws SAXException {
        if (isSuccess) {
            super.setResult(pResult);
        } else {
            Map<Object, Object> map = (Map) pResult;
            Integer faultCode = (Integer) map.get("faultCode");
            if (faultCode == null) {
                throw new SAXParseException("Missing faultCode", getDocumentLocator());
            }
            try {
                errorCode = faultCode;
            } catch (NumberFormatException e) {
                throw new SAXParseException("Invalid faultCode: " + faultCode,
                        getDocumentLocator());
            }
            errorMessage = (String) map.get("faultString");
            Object exception = map.get("faultCause");
            if (exception != null) {
                try {
                    byte[] bytes = (byte[]) exception;
                    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
                    ObjectInputStream ois = new ObjectInputStream(bais);
                    errorCause = (Throwable) ois.readObject();
                    ois.close();
                    bais.close();
                } catch (Throwable t) {
                    // Ignore me
                }
            }
        }
    }

    public void startDocument() throws SAXException {
        super.startDocument();
        level = 0;
        isSuccess = false;
        errorCode = 0;
        errorMessage = null;
    }

    public void startElement(String pURI, String pLocalName, String pQName,
                             Attributes pAttrs) throws SAXException {
        switch (level++) {
            case 0:
                if (!"".equals(pURI)  ||  !"methodResponse".equals(pLocalName)) {
                    throw new SAXParseException("Expected methodResponse element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 1:
                if ("".equals(pURI)  &&  "params".equals(pLocalName)) {
                    isSuccess = true;
                } else if ("".equals(pURI)  &&  "fault".equals(pLocalName)) {
                    isSuccess = false;
                } else {
                    throw new SAXParseException("Expected params or fault element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 2:
                if (isSuccess) {
                    if (!"".equals(pURI)  ||  !"param".equals(pLocalName)) {
                        throw new SAXParseException("Expected param element, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else {
                    if ("".equals(pURI)  &&  "value".equals(pLocalName)) {
                        startValueTag();
                    } else {
                        throw new SAXParseException("Expected value element, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                }
                break;
            case 3:
                if (isSuccess) {
                    if ("".equals(pURI)  &&  "value".equals(pLocalName)) {
                        startValueTag();
                    } else {
                        throw new SAXParseException("Expected value element, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else {
                    super.startElement(pURI, pLocalName, pQName, pAttrs);
                }
                break;
            default:
                super.startElement(pURI, pLocalName, pQName, pAttrs);
                break;
        }
    }

    public void endElement(String pURI, String pLocalName, String pQName) throws SAXException {
        switch (--level) {
            case 0:
                if (!"".equals(pURI)  ||  !"methodResponse".equals(pLocalName)) {
                    throw new SAXParseException("Expected /methodResponse element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            case 1:
            {
                String tag;
                if (isSuccess) {
                    tag = "params";
                } else {
                    tag = "fault";
                }
                if (!"".equals(pURI)  ||  !tag.equals(pLocalName)) {
                    throw new SAXParseException("Expected /" + tag + " element, got "
                            + new QName(pURI, pLocalName),
                            getDocumentLocator());
                }
                break;
            }
            case 2:
                if (isSuccess) {
                    if (!"".equals(pURI)  ||  !"param".equals(pLocalName)) {
                        throw new SAXParseException("Expected /param, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else {
                    if ("".equals(pURI)  &&  "value".equals(pLocalName)) {
                        endValueTag();
                    } else {
                        throw new SAXParseException("Expected /value, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                }
                break;
            case 3:
                if (isSuccess) {
                    if ("".equals(pURI)  &&  "value".equals(pLocalName)) {
                        endValueTag();
                    } else {
                        throw new SAXParseException("Expected /value, got "
                                + new QName(pURI, pLocalName),
                                getDocumentLocator());
                    }
                } else {
                    super.endElement(pURI, pLocalName, pQName);
                }
                break;
            default:
                super.endElement(pURI, pLocalName, pQName);
                break;
        }
    }

    /** Returns whether the response returned success. If so, the
     * result object may be fetched using {@link #getResult()}.
     * Otherwise, you may use the methods
     * {@link #getErrorCode()} and {@link #getErrorMessage()} to
     * check for error reasons.
     * @return True, if the response indicated success, false otherwise.
     */
    public boolean isSuccess() { return isSuccess; }

    /** If the response contained a fault, returns the error code.
     * @return The numeric error code.
     */
    public int getErrorCode() { return errorCode; }

    /** If the response contained a fault, returns the error message.
     * @return The error message.
     */
    public String getErrorMessage() { return errorMessage; }

    /**
     * If the response contained a fault, returns the (optional)
     * exception.
     * @return fault
     */
    public Throwable getErrorCause() { return errorCause; }
}
