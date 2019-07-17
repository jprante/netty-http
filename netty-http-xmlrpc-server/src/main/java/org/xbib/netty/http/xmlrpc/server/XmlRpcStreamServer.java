package org.xbib.netty.http.xmlrpc.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.xbib.netty.http.xmlrpc.common.ServerStreamConnection;
import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequestConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestProcessor;
import org.xbib.netty.http.xmlrpc.common.parser.XmlRpcRequestParser;
import org.xbib.netty.http.xmlrpc.common.serializer.DefaultXMLWriterFactory;
import org.xbib.netty.http.xmlrpc.common.serializer.XmlRpcWriter;
import org.xbib.netty.http.xmlrpc.common.serializer.XmlWriterFactory;
import org.xbib.netty.http.xmlrpc.common.util.SAXParsers;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/** Extension of {@link XmlRpcServer} with support for reading
 * requests from a stream and writing the response to another
 * stream.
 */
public abstract class XmlRpcStreamServer extends XmlRpcServer
        implements XmlRpcStreamRequestProcessor {

    private XmlWriterFactory writerFactory = new DefaultXMLWriterFactory();

    private static final XmlRpcErrorLogger theErrorLogger = new XmlRpcErrorLogger();

    private XmlRpcErrorLogger errorLogger = theErrorLogger;

    protected XmlRpcRequest getRequest(final XmlRpcStreamRequestConfig pConfig,
                                       InputStream pStream) throws XmlRpcException {
        final XmlRpcRequestParser parser = new XmlRpcRequestParser(pConfig, getTypeFactory());
        final XMLReader xr = SAXParsers.newXMLReader();
        xr.setContentHandler(parser);
        try {
            xr.parse(new InputSource(pStream));
        } catch (SAXException e) {
            Exception ex = e.getException();
            if (ex instanceof XmlRpcException) {
                throw (XmlRpcException) ex;
            }
            throw new XmlRpcException("Failed to parse XML-RPC request: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XmlRpcException("Failed to read XML-RPC request: " + e.getMessage(), e);
        }
        final List<Object> params = parser.getParams();
        return new XmlRpcRequest(){

            @Override
            public XmlRpcRequestConfig getConfig() { return pConfig; }

            @Override
            public String getMethodName() { return parser.getMethodName(); }

            @Override
            public int getParameterCount() { return params == null ? 0 : params.size(); }

            @Override
            public Object getParameter(int pIndex) { return params.get(pIndex); }
        };
    }

    protected XmlRpcWriter getXmlRpcWriter(XmlRpcStreamRequestConfig pConfig,
                                           OutputStream pStream)
            throws XmlRpcException {
        ContentHandler w = getXMLWriterFactory().getXmlWriter(pConfig, pStream);
        return new XmlRpcWriter(pConfig, w, getTypeFactory());
    }

    protected void writeResponse(XmlRpcStreamRequestConfig pConfig, OutputStream pStream,
                                 Object pResult) throws XmlRpcException {
        try {
            getXmlRpcWriter(pConfig, pStream).write(pConfig, pResult);
        } catch (SAXException e) {
            throw new XmlRpcException("Failed to write XML-RPC response: " + e.getMessage(), e);
        }
    }

    /**
     * This method allows to convert the error into another error. For example, this
     * may be an error, which could be deserialized by the client.
     */
    protected Throwable convertThrowable(Throwable pError) {
        return pError;
    }

    protected void writeError(XmlRpcStreamRequestConfig pConfig, OutputStream pStream,
                              Throwable pError)
            throws XmlRpcException {
        final Throwable error = convertThrowable(pError);
        final int code;
        final String message;
        if (error instanceof XmlRpcException) {
            XmlRpcException ex = (XmlRpcException) error;
            code = ex.code;
        } else {
            code = 0;
        }
        message = error.getMessage();
        try {
            getXmlRpcWriter(pConfig, pStream).write(pConfig, code, message, error);
        } catch (SAXException e) {
            throw new XmlRpcException("Failed to write XML-RPC response: " + e.getMessage(), e);
        }
    }

    /** Sets the XML Writer factory.
     * @param pFactory The XML Writer factory.
     */
    public void setXMLWriterFactory(XmlWriterFactory pFactory) {
        writerFactory = pFactory;
    }

    /** Returns the XML Writer factory.
     * @return The XML Writer factory.
     */
    public XmlWriterFactory getXMLWriterFactory() {
        return writerFactory;
    }

    protected InputStream getInputStream(XmlRpcStreamRequestConfig pConfig,
                                         ServerStreamConnection pConnection) throws IOException {
        InputStream istream = pConnection.newInputStream();
        if (pConfig.isEnabledForExtensions()  &&  pConfig.isGzipCompressing()) {
            istream = new GZIPInputStream(istream);
        }
        return istream;
    }

    /** Called to prepare the output stream. Typically used for enabling
     * compression, or similar filters.
     * @param pConnection The connection object.
     */
    protected OutputStream getOutputStream(ServerStreamConnection pConnection,
                                           XmlRpcStreamRequestConfig pConfig, OutputStream pStream) throws IOException {
        if (pConfig.isEnabledForExtensions()  &&  pConfig.isGzipRequesting()) {
            return new GZIPOutputStream(pStream);
        } else {
            return pStream;
        }
    }

    /** Called to prepare the output stream, if content length is
     * required.
     * @param pConfig The configuration object.
     * @param pSize The requests size.
     */
    protected OutputStream getOutputStream(XmlRpcStreamRequestConfig pConfig,
                                           ServerStreamConnection pConnection,
                                           int pSize) throws IOException {
        return pConnection.newOutputStream();
    }

    /** Returns, whether the requests content length is required.
     * @param pConfig The configuration object.
     */
    protected boolean isContentLengthRequired(XmlRpcStreamRequestConfig pConfig) {
        return false;
    }

    /**
     * Processes a "connection". The "connection" is an opaque object, which is
     * being handled by the subclasses.
     * @param pConfig The request configuration.
     * @param pConnection The "connection" being processed.
     * @throws XmlRpcException Processing the request failed.
     */
    @Override
    public void execute(XmlRpcStreamRequestConfig pConfig, ServerStreamConnection pConnection)
            throws XmlRpcException {
        try {
            Object result;
            Throwable error;
            InputStream istream = null;
            try {
                istream = getInputStream(pConfig, pConnection);
                XmlRpcRequest request = getRequest(pConfig, istream);
                result = execute(request);
                istream.close();
                istream = null;
                error = null;
            } catch (Throwable t) {
                logError(t);
                result = null;
                error = t;
            } finally {
                if (istream != null) {
                    try {
                        istream.close();
                    } catch (Throwable ignore) {
                        //
                    }
                }
            }
            boolean contentLengthRequired = isContentLengthRequired(pConfig);
            ByteArrayOutputStream baos;
            OutputStream ostream;
            if (contentLengthRequired) {
                baos = new ByteArrayOutputStream();
                ostream = baos;
            } else {
                baos = null;
                ostream = pConnection.newOutputStream();
            }
            ostream = getOutputStream(pConnection, pConfig, ostream);
            try {
                if (error == null) {
                    writeResponse(pConfig, ostream, result);
                } else {
                    writeError(pConfig, ostream, error);
                }
                ostream.close();
                ostream = null;
            } finally {
                if (ostream != null) {
                    try {
                        ostream.close();
                    } catch (Throwable ignore) {

                    }
                }
            }
            if (baos != null) {
                OutputStream dest = getOutputStream(pConfig, pConnection, baos.size());
                try {
                    baos.writeTo(dest);
                    dest.close();
                    dest = null;
                } finally {
                    if (dest != null) {
                        try {
                            dest.close();
                        } catch (Throwable ignore) {

                        }
                    }
                }
            }
            pConnection.close();
            pConnection = null;
        } catch (IOException e) {
            throw new XmlRpcException("I/O error while processing request: " + e.getMessage(), e);
        } finally {
            if (pConnection != null) {
                try {
                    pConnection.close(); } catch (Throwable ignore) {

                }
            }
        }
    }

    protected void logError(Throwable t) {
        final String msg = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
        errorLogger.log(msg, t);
    }

    /**
     * Returns the error logger.
     */
    public XmlRpcErrorLogger getErrorLogger() {
        return errorLogger;
    }

    /**
     * Sets the error logger.
     */
    public void setErrorLogger(XmlRpcErrorLogger pErrorLogger) {
        errorLogger = pErrorLogger;
    }
}
