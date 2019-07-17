package org.xbib.netty.http.xmlrpc.servlet;

import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;
import org.xbib.netty.http.xmlrpc.common.util.HttpUtil;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.UndeclaredThrowableException;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.ReadListener;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

/**
 * Stub implementation of a {@link javax.servlet.http.HttpServletRequest}
 * with lots of unimplemented methods. I implemented only those, which
 * are required for testing the {@link XmlRpcServlet}.
 * Perhaps someone else is adding more at a later time?
 */
public class HttpServletRequestImpl implements HttpServletRequest {

    private final Socket socket;

    private final ServletInputStream istream;

    private ServletInputStream sistream;

    private BufferedReader reader;

    private boolean postParametersParsed;

    private String method;

    private String protocol;

    private String uri;

    private String queryString;

    private final Map<String, String[]> headers = new HashMap<>();

    private final Map<String, Object> attributes = new HashMap<>();

    private Map<String, String[]> parameters;

    private String characterEncoding;

    private int contentBytesRemaining = -1;

    /** Creates a new instance, which reads input from the given
     * socket.
     * @param pSocket The socket, to which the client is connected.
     * @throws IOException Accessing the sockets input stream failed.
     */
    public HttpServletRequestImpl(Socket pSocket) throws IOException {
        socket = pSocket;
        final InputStream bis = new BufferedInputStream(socket.getInputStream()){
            /** It may happen, that the XML parser invokes close().
             * Closing the input stream must not occur, because
             * that would close the whole socket. So we suppress it.
             */
            @Override
            public void close(){
            }
        };
        istream = new ServletInputStream(){

            @Override
            public boolean isFinished() {
                return contentBytesRemaining == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // do nothing
            }

            @Override
            public int read() throws IOException {
                if (contentBytesRemaining == 0) {
                    return -1;
                }
                int c = bis.read();
                if (c != -1  &&  contentBytesRemaining > 0) {
                    --contentBytesRemaining;
                }
                return c;
            }
        };
    }

    /**
     * Read the header lines, one by one. Note, that the size of
     * the buffer is a limitation of the maximum header length!
     */
    void readHttpHeaders() throws IOException {
        byte[] buffer = new byte[2048];
        String line = readLine(buffer);
        StringTokenizer tokens = line != null ? new StringTokenizer(line) : null;
        if (tokens == null || !tokens.hasMoreTokens()) {
            throw new ServletWebServer.ServletWebServerException(400, "Bad Request", "Unable to parse requests first line (should" +
                    " be 'METHOD uri HTTP/version', was empty.");
        }
        method = tokens.nextToken();
        if (!"POST".equalsIgnoreCase(method)) {
            throw new ServletWebServer.ServletWebServerException(400, "Bad Request", "Expected 'POST' method, got " +
                    method);
        }
        if (!tokens.hasMoreTokens()) {
            throw new ServletWebServer.ServletWebServerException(400, "Bad Request", "Unable to parse requests first line (should" +
                    " be 'METHOD uri HTTP/version', was: " + line);
        }
        String u = tokens.nextToken();
        int offset = u.indexOf('?');
        if (offset >= 0) {
            uri = u.substring(0, offset);
            queryString = u.substring(offset + 1);
        } else {
            uri = u;
            queryString = null;
        }
        if (tokens.hasMoreTokens()) {
            String v = tokens.nextToken().toUpperCase();
            if (tokens.hasMoreTokens()) {
                throw new ServletWebServer.ServletWebServerException(400, "Bad Request",
                        "Unable to parse requests first line (should" + " be 'METHOD uri HTTP/version', was: " + line);
            } else {
                int index = v.indexOf('/');
                if (index == -1) {
                    throw new ServletWebServer.ServletWebServerException(400, "Bad Request",
                            "Unable to parse requests first line (should" + " be 'METHOD uri HTTP/version', was: " + line);
                }
                protocol = v.substring(0, index).toUpperCase();
            }
        } else {
            protocol = "HTTP";
        }
        while (true) {
            line = HttpUtil.readLine(istream, buffer);
            if (line.length() == 0) {
                break;
            }
            int off = line.indexOf(':');
            if (off > 0) {
                addHeader(line.substring(0, off), line.substring(off + 1).trim());
            } else {
                throw new ServletWebServer.ServletWebServerException(400, "Bad Request",
                        "Unable to parse header line: " + line);
            }
        }
        contentBytesRemaining = getIntHeader("content-length");
    }

    private String readLine(byte[] pBuffer) throws IOException {
        int res = istream.readLine(pBuffer, 0, pBuffer.length);
        if (res == -1) {
            return null;
        }
        if (res == pBuffer.length && pBuffer[pBuffer.length - 1] != '\n') {
            throw new ServletWebServer.ServletWebServerException(400, "Bad Request",
                    "maximum header size of " + pBuffer.length + " characters exceeded");
        }
        return new String(pBuffer, 0, res, StandardCharsets.US_ASCII);
    }

    private void addHeader(String pHeader, String pValue) {
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
    public String getAuthType() {
        String s = getHeader("Authorization");
        if (s == null) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(s);
        if (st.hasMoreTokens()) {
            return st.nextToken().toUpperCase();
        } else {
            return null;
        }
    }

    @Override
    public String getContextPath() { return ""; }

    @Override
    public Cookie[] getCookies() { throw new IllegalStateException("Not implemented"); }

    @Override
    public long getDateHeader(String arg0) { throw new IllegalStateException("Not implemented"); }

    @Override
    public String getHeader(String pHeader) {
        String key = pHeader.toLowerCase();
        String[] strings = headers.get(key);
        return strings != null && strings.length > 0 ? strings[0] : null;
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public Enumeration<String> getHeaders(String pHeader) {
        String key = pHeader.toLowerCase();
        String[] values = headers.get(key);
        return values != null && values.length > 0 ?
                Collections.enumeration(Arrays.asList(values)) : Collections.emptyEnumeration();
    }

    @Override
    public int getIntHeader(String pHeader) {
        String s = getHeader(pHeader);
        return s == null ? -1 : Integer.parseInt(s);
    }

    @Override
    public String getMethod() { return method; }

    @Override
    public String getPathInfo() { return null; }

    @Override
    public String getPathTranslated() { return null; }

    @Override
    public String getQueryString() { return queryString; }

    @Override
    public String getRemoteUser() { throw new IllegalStateException("Not implemented"); }

    @Override
    public String getRequestURI() { return uri; }

    @Override
    public StringBuffer getRequestURL() {
        String scheme = getScheme().toLowerCase();
        StringBuffer sb = new StringBuffer(scheme);
        sb.append("://");
        String host = getHeader("host");
        if (host == null) {
            host = getLocalName();
            if (host == null) {
                host = getLocalAddr();
            }
        }
        int port = getLocalPort();
        int offset = host.indexOf(':');
        if (offset != -1) {
            host = host.substring(0, offset);
            try {
                port = Integer.parseInt(host.substring(offset+1));
            } catch (Exception e) {
                //
            }
        }
        boolean isDefaultPort;
        if ("http".equalsIgnoreCase(scheme)) {
            isDefaultPort = port == 80;
        } else if ("https".equalsIgnoreCase(scheme)) {
            isDefaultPort = port == 443;
        } else {
            isDefaultPort = false;
        }
        if (!isDefaultPort) {
            sb.append(':');
            sb.append(port);
        }
        sb.append(getRequestURI());
        return sb;
    }

    @Override
    public String getRequestedSessionId() { throw new IllegalStateException("Not implemented"); }

    @Override
    public String getServletPath() { return uri; }

    @Override
    public HttpSession getSession() { throw new IllegalStateException("Not implemented"); }

    @Override
    public String changeSessionId() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public HttpSession getSession(boolean pCreate) { throw new IllegalStateException("Not implemented"); }

    @Override
    public Principal getUserPrincipal() { throw new IllegalStateException("Not implemented"); }

    @Override
    public boolean isRequestedSessionIdFromCookie() { throw new IllegalStateException("Not implemented"); }

    @Override
    public boolean isRequestedSessionIdFromURL() { throw new IllegalStateException("Not implemented"); }

    @Override
    public boolean isRequestedSessionIdFromUrl() { throw new IllegalStateException("Not implemented"); }

    @Override
    public boolean authenticate(HttpServletResponse httpServletResponse) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void login(String user, String password) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public void logout() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Collection<Part> getParts() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Part getPart(String s) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean isRequestedSessionIdValid() { throw new IllegalStateException("Not implemented"); }

    @Override
    public boolean isUserInRole(String pRole) { throw new IllegalStateException("Not implemented"); }

    @Override
    public Object getAttribute(String pKey) { return attributes.get(pKey); }

    @Override
    public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            String contentType = getHeader("content-type");
            if (contentType != null) {
                for (StringTokenizer st = new StringTokenizer(contentType, ";");  st.hasMoreTokens();  ) {
                    String s = st.nextToken().trim();
                    if (s.toLowerCase().startsWith("charset=")) {
                        return s.substring("charset=".length()).trim();
                    }
                }
            }
            return null;
        } else {
            return characterEncoding;
        }
    }

    @Override
    public void setCharacterEncoding(String pEncoding) { characterEncoding = pEncoding; }

    @Override
    public int getContentLength() {
        try {
            return getIntHeader("content-length");
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public long getContentLengthLong() {
        return 0;
    }

    @Override
    public String getContentType() { return getHeader("content-type"); }

    @Override
    public ServletInputStream getInputStream() {
        if (reader == null) {
            if (sistream == null) {
                if (postParametersParsed) {
                    throw new IllegalStateException("The method getInputStream() must not be called, after POST parameters have been parsed.");
                }
                sistream = istream;
            }
            return sistream;
        } else {
            throw new IllegalStateException("The method getReader() has already been invoked.");
        }
    }

    @Override
    public Locale getLocale() { throw new IllegalStateException("Not implemented"); }

    @Override
    public Enumeration<Locale> getLocales() { throw new IllegalStateException("Not implemented"); }

    private void parseQueryString(Map<String, String[]> pParams, String pQueryString, String pEncoding) throws UnsupportedEncodingException {
        for (StringTokenizer st = new StringTokenizer(pQueryString, "&");  st.hasMoreTokens();  ) {
            String s = st.nextToken();
            parseParameter(pParams, s, pEncoding);
        }
    }

    private void parseParameter(Map<String, String[]> pParams, String pParam, String pEncoding) throws UnsupportedEncodingException {
        if (pParam.length() == 0) {
            return;
        }
        int offset = pParam.indexOf('=');
        final String name, value;
        if (offset == -1) {
            name = pParam;
            value = "";
        } else {
            name = pParam.substring(0, offset);
            value = pParam.substring(offset+1);
        }
        //addParameter(pParams, URLDecoder.decode(name, pEncoding), URLDecoder.decode(value, pEncoding));
        pParams.put(URLDecoder.decode(name, pEncoding), new String[] { URLDecoder.decode(value, pEncoding)});
    }

    private void parsePostData(Map<String, String[]> pParams, InputStream pStream, String pEncoding) throws IOException {
        Reader r = new InputStreamReader(pStream, StandardCharsets.US_ASCII);
        StringBuilder sb = new StringBuilder();
        for (;;) {
            int c = r.read();
            if (c == -1  ||  c == '&') {
                parseParameter(pParams, sb.toString(), pEncoding);
                if (c == -1) {
                    break;
                } else {
                    sb.setLength(0);
                }
            } else {
                sb.append((char) c);
            }
        }
    }

    private void parseParameters() {
        if (parameters != null) {
            return;
        }
        String encoding = getCharacterEncoding();
        if (encoding == null) {
            encoding = XmlRpcStreamConfig.UTF8_ENCODING;
        }
        Map<String, String[]> params = new HashMap<>();
        String s = getQueryString();
        if (s != null) {
            try {
                parseQueryString(params, s, encoding);
            } catch (IOException e) {
                throw new UndeclaredThrowableException(e);
            }
        }
        if ("POST".equals(getMethod())  &&
                "application/x-www-form-urlencoded".equals(getContentType())) {
            if (sistream != null  ||  reader != null) {
                throw new IllegalStateException("POST parameters cannot be parsed, after"
                        + " getInputStream(), or getReader(),"
                        + " have been called.");
            }
            postParametersParsed = true;
            try {
                parsePostData(params, istream, encoding);
            } catch (IOException e) {
                throw new UndeclaredThrowableException(e);
            }
        }
        parameters = params;
    }

    @Override
    public String getParameter(String pName) {
        parseParameters();
        String[] strings = parameters.get(pName);
        return strings != null ? strings[0] : null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String[]> getParameterMap() {
        parseParameters();
        final Map<String, String[]> result = new HashMap<>();
        for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        parseParameters();
        return Collections.enumeration(parameters.keySet());
    }

    @Override
    public String[] getParameterValues(String pName) {
        parseParameters();
        return parameters.get(pName);
    }

    public String getProtocol() { return protocol; }

    public BufferedReader getReader() throws IOException {
        if (sistream == null) {
            if (reader == null) {
                if (postParametersParsed) {
                    throw new IllegalStateException("The method getReader() must not be called, after POST parameters have been parsed.");
                }
                String encoding = getCharacterEncoding();
                if (encoding == null) {
                    encoding = "UTF8";
                }
                reader = new BufferedReader(new InputStreamReader(istream, encoding));
            }
            return reader;
        } else {
            throw new IllegalStateException("The methods getInputStream(), and getReader(), are mutually exclusive.");
        }
    }

    public String getRealPath(String pPath) { throw new IllegalStateException("Not implemented."); }

    public String getLocalAddr() { return socket.getLocalAddress().getHostAddress(); }

    public String getLocalName() { return socket.getLocalAddress().getHostName(); }

    public int getLocalPort() { return socket.getLocalPort(); }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return null;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return null;
    }

    @Override
    public String getRemoteAddr() { return socket.getInetAddress().getHostAddress(); }

    @Override
    public String getRemoteHost() { return socket.getInetAddress().getHostName(); }

    @Override
    public int getRemotePort() { return socket.getPort(); }

    @Override
    public RequestDispatcher getRequestDispatcher(String pUri) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getScheme() { return "http"; }

    @Override
    public String getServerName() { return socket.getLocalAddress().getHostName(); }

    @Override
    public int getServerPort() { return socket.getLocalPort(); }

    @Override
    public boolean isSecure() { return false; }

    @Override
    public void removeAttribute(String pKey) {
        attributes.remove(pKey);
    }

    @Override
    public void setAttribute(String pKey, Object pValue) {
        attributes.put(pKey, pValue);
    }
}
