package org.xbib.netty.http.xmlrpc.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Stub implementation of a {@link javax.servlet.http.HttpServletResponse}
 * with lots of unimplemented methods. I implemented only those, which
 * are required for testing the {@link XmlRpcServlet}.
 * Perhaps someone else is adding more at a later time?
 */
public class HttpServletResponseImpl implements HttpServletResponse {

    static final int BUFFER_SIZE = 8192;

    private final OutputStream ostream;

    private final Map<String, String[]> headers = new HashMap<>();

    private int status = HttpServletResponse.SC_OK;

    private String message = getStatusMessage(status);

    private Locale locale;

    private String charEncoding;

    private PrintWriter writer;

    private ServletOutputStreamImpl soStream;

    /** Creates a new instance.
     * @param pSocket The clients socket.
     * @throws IOException Accessing the sockets output stream failed.
     */
    HttpServletResponseImpl(Socket pSocket) throws IOException {
        ostream = pSocket.getOutputStream();
    }

    @Override
    public void addCookie(Cookie pCookie) { throw new IllegalStateException("Not implemented"); }

    @Override
    public void addDateHeader(String pHeader, long pDate) { throw new IllegalStateException("Not implemented"); }

    @Override
    public void addHeader(String pHeader, String pValue) {
        String key = pHeader.toLowerCase();
        String[] strings = headers.get(key);
        if (strings == null) {
            strings = new String[] { pValue };
        } else {
            List<String> list = new ArrayList<>(Arrays.asList(strings));
            list.add(pValue);
            strings = list.toArray(new String[0]);
        }
        headers.put(key, strings);
    }

    @Override
    public String getHeader(String pHeader) {
        String key = pHeader.toLowerCase();
        String[] strings = headers.get(key);
        return strings != null && strings.length > 0 ? strings[0] : null;
    }

    @Override
    public Collection<String> getHeaders(String pHeader) {
        String key = pHeader.toLowerCase();
        return Arrays.asList(headers.get(key));
    }

    @Override
    public Collection<String> getHeaderNames() {
        return headers.keySet();
    }

    @Override
    public void addIntHeader(String pHeader, int pValue) {
        addHeader(pHeader, Integer.toString(pValue));
    }

    @Override
    public boolean containsHeader(String pHeader) {
        return headers.containsKey(pHeader.toLowerCase());
    }

    @Override
    public String encodeRedirectURL(String pURL) { throw new IllegalStateException("Not implemented"); }

    @Override
    public String encodeRedirectUrl(String pURL) { return encodeRedirectURL(pURL); }

    @Override
    public String encodeURL(String pURL) { throw new IllegalStateException("Not implemented"); }

    @Override
    public String encodeUrl(String pURL) { throw new IllegalStateException("Not implemented"); }

    @Override
    public void sendError(int pStatusCode) throws IOException {
        sendError(pStatusCode, getStatusMessage(pStatusCode));
    }

    @Override
    public void sendError(int pStatusCode, String pMessage) throws IOException {
        sendError(pStatusCode, pMessage, null);
    }

    protected void sendError(int pStatusCode, String pMessage, String pDescription) throws IOException {
        if (isCommitted()) {
            throw new IllegalStateException("Can't send an error message, if the response has already been committed.");
        }
        headers.clear();
        setContentType("text/html");
        setStatus(pStatusCode, pMessage);
        if (soStream == null) {
            soStream = new ServletOutputStreamImpl(ostream, this);
        } else {
            soStream.reset();
        }
        OutputStreamWriter osw = new OutputStreamWriter(soStream, getCharacterEncoding());
        osw.write("<html><head><title>" + pStatusCode + " " + pMessage + "</title></head>\r\n");
        osw.write("<body><h1>" + pStatusCode + " " + pMessage + "</h1>\r\n");
        if (pDescription != null) {
            osw.write("<p>" + pDescription + "</p>\r\n");
        }
        osw.write("</body></html>\r\n");
        osw.close();
    }

    @Override
    public void sendRedirect(String arg0) throws IOException { throw new IllegalStateException("Not implemented"); }

    @Override
    public void setDateHeader(String arg0, long arg1) { throw new IllegalStateException("Not implemented"); }

    @Override
    public void setHeader(String pHeader, String pValue) {
        headers.remove(pHeader.toLowerCase());
        addHeader(pHeader, pValue);
    }

    @Override
    public void setIntHeader(String pHeader, int pValue) {
        setHeader(pHeader, Integer.toString(pValue));
    }

    @Override
    public void setStatus(int pStatusCode) {
        setStatus(pStatusCode, getStatusMessage(pStatusCode));
    }

    @Override
    public void setStatus(int pStatusCode, String pMessage) {
        status = pStatusCode;
        message = pMessage;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void flushBuffer() throws IOException {
        ostream.flush();
    }

    @Override
    public int getBufferSize() { return BUFFER_SIZE; }

    /** <p>Sets the character encoding (MIME charset) of the response being sent
     * to the client, for example, to UTF-8. If the character encoding has
     * already been set by setContentType(java.lang.String) or
     * setLocale(java.util.Locale), this method overrides it.
     * Calling setContentType(java.lang.String) with the String
     * of text/html and calling this method with the String of UTF-8
     * is equivalent with calling setContentType with the String of
     * text/html; charset=UTF-8.</p>
     * <p>This method can be called repeatedly to change the character
     * encoding. This method has no effect if it is called after getWriter
     * has been called or after the response has been committed.</p>
     * <p>Containers must communicate the character encoding used for
     * the servlet response's writer to the client if the protocol
     * provides a way for doing so. In the case of HTTP, the character
     * encoding is communicated as part of the Content-Type header for
     * text media types. Note that the character encoding cannot be
     * communicated via HTTP headers if the servlet does not specify
     * a content type; however, it is still used to encode text written
     * via the servlet response's writer.</p>
     * @param pCharset A String specifying only the character set defined
     * by IANA Character Sets (http://www.iana.org/assignments/character-sets)
     * @since Servlet API 2.4
     * @see #setLocale(Locale)
     */
    @Override
    public void setCharacterEncoding(String pCharset) {
        charEncoding = pCharset;
    }

    @Override
    public String getCharacterEncoding() {
        if (charEncoding == null) {
            return "ISO-8859-1";
        } else {
            return charEncoding;
        }
    }

    @Override
    public Locale getLocale() { return locale; }

    @Override
    public ServletOutputStream getOutputStream() {
        if (writer != null) {
            throw new IllegalStateException("You may call either getWriter() or getOutputStream(), but not both.");
        } else {
            if (soStream == null) {
                soStream = new ServletOutputStreamImpl(ostream, this);
            }
            return soStream;
        }
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer != null) {
            return writer;
        } else if (soStream != null) {
            throw new IllegalStateException("You may call either getWriter() or getOutputStream(), but not both.");
        } else {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), getCharacterEncoding()));
            return writer;
        }
    }

    @Override
    public boolean isCommitted() {
        return soStream != null  &&  soStream.isCommitted();
    }

    @Override
    public void reset() {
        resetBuffer();
        setStatus(HttpServletResponse.SC_OK);
        headers.clear();
        charEncoding = null;
        locale = null;
    }

    @Override
    public void resetBuffer() {
        if (isCommitted()) {
            throw new IllegalStateException("The ServletOutputStream is already committed. A reset is no longer possible.");
        }
        if (soStream != null) {
            soStream.reset();
        }
    }

    @Override
    public void setBufferSize(int pBufferSize) { throw new IllegalStateException("Not implemented"); }

    @Override
    public void setContentLength(int pContentLength) {
        if (pContentLength == -1) {
            headers.remove("content-length");
        } else {
            setIntHeader("content-length", pContentLength);
        }
    }

    @Override
    public void setContentLengthLong(long pContentLength) {
        if (pContentLength == -1) {
            headers.remove("content-length");
        } else {
            setHeader("content-length", Long.toString(pContentLength));
        }
    }

    /** <p>Returns the content type used for the MIME body sent in this
     * response. The content type proper must have been specified
     * using setContentType(java.lang.String) before the response is
     * committed. If no content type has been specified, this method
     * returns null. If a content type has been specified and a
     * character encoding has been explicitly or implicitly specified
     * as described in getCharacterEncoding(), the charset parameter
     * is included in the string returned. If no character encoding
     * has been specified, the charset parameter is omitted.</p>
     * @return A String specifying the content type, for example,
     * text/html; charset=UTF-8, or null
     * @since Servlet API 2.4
     * @see #setContentType(String)
     */
    @Override
    public String getContentType() {
        String s = getHeader("content-type");
        if (s != null  &&  s.toLowerCase().startsWith("text/")) {
            String enc = getCharacterEncoding();
            if (enc != null) {
                s += "; charset=" + enc;
            }
        }
        return s;
    }

    @Override
    public void setContentType(String pType) {
        if (pType != null) {
            boolean charSetFound = false;
            StringBuilder sb = new StringBuilder();
            for (StringTokenizer st = new StringTokenizer(pType, ";");  st.hasMoreTokens();  ) {
                String t = st.nextToken();
                if (t.toLowerCase().startsWith("charset=")) {
                    charSetFound = true;
                    setCharacterEncoding(t.substring("charset=".length()).trim());
                } else {
                    if (sb.length() > 0) {
                        sb.append("; ");
                    }
                    sb.append(t);
                }
            }
            if (charSetFound) {
                pType = sb.toString();
            }
        }
        setHeader("content-type", pType);
    }

    @Override
    public void setLocale(Locale pLocale) { locale = pLocale; }

    /** Returns a default message for a given HTTP status code.
     * @param pStatusCode The status code being queried.
     * @return The default message.
     */
    public static String getStatusMessage(int pStatusCode) {
        switch (pStatusCode) {
            case HttpServletResponse.SC_OK:
                return ("OK");
            case HttpServletResponse.SC_ACCEPTED:
                return ("Accepted");
            case HttpServletResponse.SC_BAD_GATEWAY:
                return ("Bad Gateway");
            case HttpServletResponse.SC_BAD_REQUEST:
                return ("Bad Request");
            case HttpServletResponse.SC_CONFLICT:
                return ("Conflict");
            case HttpServletResponse.SC_CONTINUE:
                return ("Continue");
            case HttpServletResponse.SC_CREATED:
                return ("Created");
            case HttpServletResponse.SC_EXPECTATION_FAILED:
                return ("Expectation Failed");
            case HttpServletResponse.SC_FORBIDDEN:
                return ("Forbidden");
            case HttpServletResponse.SC_GATEWAY_TIMEOUT:
                return ("Gateway Timeout");
            case HttpServletResponse.SC_GONE:
                return ("Gone");
            case HttpServletResponse.SC_HTTP_VERSION_NOT_SUPPORTED:
                return ("HTTP Version Not Supported");
            case HttpServletResponse.SC_INTERNAL_SERVER_ERROR:
                return ("Internal Server Error");
            case HttpServletResponse.SC_LENGTH_REQUIRED:
                return ("Length Required");
            case HttpServletResponse.SC_METHOD_NOT_ALLOWED:
                return ("Method Not Allowed");
            case HttpServletResponse.SC_MOVED_PERMANENTLY:
                return ("Moved Permanently");
            case HttpServletResponse.SC_MOVED_TEMPORARILY:
                return ("Moved Temporarily");
            case HttpServletResponse.SC_MULTIPLE_CHOICES:
                return ("Multiple Choices");
            case HttpServletResponse.SC_NO_CONTENT:
                return ("No Content");
            case HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION:
                return ("Non-Authoritative Information");
            case HttpServletResponse.SC_NOT_ACCEPTABLE:
                return ("Not Acceptable");
            case HttpServletResponse.SC_NOT_FOUND:
                return ("Not Found");
            case HttpServletResponse.SC_NOT_IMPLEMENTED:
                return ("Not Implemented");
            case HttpServletResponse.SC_NOT_MODIFIED:
                return ("Not Modified");
            case HttpServletResponse.SC_PARTIAL_CONTENT:
                return ("Partial Content");
            case HttpServletResponse.SC_PAYMENT_REQUIRED:
                return ("Payment Required");
            case HttpServletResponse.SC_PRECONDITION_FAILED:
                return ("Precondition Failed");
            case HttpServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED:
                return ("Proxy Authentication Required");
            case HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE:
                return ("Request Entity Too Large");
            case HttpServletResponse.SC_REQUEST_TIMEOUT:
                return ("Request Timeout");
            case HttpServletResponse.SC_REQUEST_URI_TOO_LONG:
                return ("Request URI Too Long");
            case HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE:
                return ("Requested Range Not Satisfiable");
            case HttpServletResponse.SC_RESET_CONTENT:
                return ("Reset Content");
            case HttpServletResponse.SC_SEE_OTHER:
                return ("See Other");
            case HttpServletResponse.SC_SERVICE_UNAVAILABLE:
                return ("Service Unavailable");
            case HttpServletResponse.SC_SWITCHING_PROTOCOLS:
                return ("Switching Protocols");
            case HttpServletResponse.SC_UNAUTHORIZED:
                return ("Unauthorized");
            case HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE:
                return ("Unsupported Media Type");
            case HttpServletResponse.SC_USE_PROXY:
                return ("Use Proxy");
            case 207:       // WebDAV
                return ("Multi-Status");
            case 422:       // WebDAV
                return ("Unprocessable Entity");
            case 423:       // WebDAV
                return ("Locked");
            case 507:       // WebDAV
                return ("Insufficient Storage");
            default:
                return ("HTTP Response Status " + pStatusCode);
        }
    }

    String getHttpHeaders(Integer pContentLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.0 ");
        sb.append(status);
        sb.append(' ');
        sb.append(message);
        sb.append("\r\n");
        String contentType = getContentType();
        if (contentType != null) {
            sb.append("Content-Type: ");
            sb.append(contentType);
            sb.append("\r\n");
        }
        boolean contentLengthSeen = false;
        for (Map.Entry<String, String[]> entry : headers.entrySet()) {
            String header = entry.getKey();
            if ("content-type".equalsIgnoreCase(header)) {
                continue;
            }
            String[] strings = entry.getValue();
            if (strings == null) {
                continue;
            }
            if ("content-length".equalsIgnoreCase(header)) {
                contentLengthSeen = true;
            }
            for (String string : strings) {
                sb.append(header);
                sb.append(": ");
                sb.append(string);
                sb.append("\r\n");
            }
        }
        if (pContentLength != null  &&  !contentLengthSeen) {
            sb.append("Content-Length: ");
            sb.append(pContentLength);
            sb.append("\r\n");
        }
        sb.append("\r\n");
        return sb.toString();
    }
}
