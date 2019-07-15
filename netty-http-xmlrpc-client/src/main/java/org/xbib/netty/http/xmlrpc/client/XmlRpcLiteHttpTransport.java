package org.xbib.netty.http.xmlrpc.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.xbib.netty.http.xmlrpc.common.XmlRpcException;
import org.xbib.netty.http.xmlrpc.common.XmlRpcRequest;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamRequestConfig;
import org.xbib.netty.http.xmlrpc.common.util.HttpUtil;
import org.xbib.netty.http.xmlrpc.common.util.LimitedInputStream;
import org.xml.sax.SAXException;

/**
 * A "light" HTTP transport implementation.
 */
public class XmlRpcLiteHttpTransport extends XmlRpcHttpTransport {
    private static final String userAgent = USER_AGENT + " (Lite HTTP Transport)";
    private boolean ssl;
    private String hostname;
    private String host;
    private int port;
    private String uri;
    private Socket socket;
    private OutputStream output;
    private InputStream input;
    private final Map<String, Object> headers = new HashMap<>();
    private boolean responseGzipCompressed = false;
    private XmlRpcHttpClientConfig config;

    /**
     * Creates a new instance.
     * @param pClient The client controlling this instance.
     */
    public XmlRpcLiteHttpTransport(XmlRpcClient pClient) {
        super(pClient, userAgent);
    }

    public Object sendRequest(XmlRpcRequest pRequest) throws XmlRpcException {
        config = (XmlRpcHttpClientConfig) pRequest.getConfig();
        URL url = config.getServerURL();
        ssl = "https".equals(url.getProtocol());
        hostname = url.getHost();
        int p = url.getPort();
        port = p < 1 ? 80 : p;
        String u = url.getFile();
        uri = (u == null  ||  "".equals(u)) ? "/" : u;
        host = port == 80 ? hostname : hostname + ":" + port;
        headers.put("Host", host);
        return super.sendRequest(pRequest);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setRequestHeader(String pHeader, String pValue) {
        Object value = headers.get(pHeader);
        if (value == null) {
            headers.put(pHeader, pValue);
        } else {
            List<Object> list;
            if (value instanceof String) {
                list = new ArrayList<>();
                list.add(value);
                headers.put(pHeader, list);
            } else {
                list = (List) value;
            }
            list.add(pValue);
        }
    }

    @Override
    protected void close() throws XmlRpcClientException {
        IOException e = null;
        if (input != null) {
            try {
                input.close();
            } catch (IOException ex) {
                e = ex;
            }
        }
        if (output != null) {
            try {
                output.close();
            } catch (IOException ex) {
                if (e != null) {
                    e = ex;
                }
            }
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                if (e != null) {
                    e = ex;
                }
            }
        }
        if (e != null) {
            throw new XmlRpcClientException("Failed to close connection: " + e.getMessage(), e);
        }
    }

    private OutputStream getOutputStream() throws XmlRpcException {
        try {
            final int retries = 3;
            final int delayMillis = 100;

            for (int tries = 0;  ;  tries++) {
                try {
                    socket = newSocket(ssl, hostname, port);
                    output = new BufferedOutputStream(socket.getOutputStream()){
                        /** Closing the output stream would close the whole socket, which we don't want,
                         * because the don't want until the request is processed completely.
                         * A close will later occur within
                         * {@link XmlRpcLiteHttpTransport#close()}.
                         */
                        @Override
                        public void close() throws IOException {
                            flush();
                            socket.shutdownOutput();
                        }
                    };
                    break;
                } catch (ConnectException e) {
                    if (tries >= retries) {
                        throw new XmlRpcException("Failed to connect to "
                                + hostname + ":" + port + ": " + e.getMessage(), e);
                    } else {
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException ignore) {
                        }
                    }
                }
            }
            sendRequestHeaders(output);
            return output;
        } catch (IOException e) {
            throw new XmlRpcException("Failed to open connection to "
                    + hostname + ":" + port + ": " + e.getMessage(), e);
        }
    }

    protected Socket newSocket(boolean pSSL, String pHostName, int pPort) throws UnknownHostException, IOException {
        if (pSSL) {
            throw new IOException("Unable to create SSL connections, use the XmlRpcLite14HttpTransportFactory.");
        }
        return new Socket(pHostName, pPort);
    }

    private byte[] toHTTPBytes(String pValue) throws UnsupportedEncodingException {
        return pValue.getBytes(StandardCharsets.US_ASCII);
    }

    private void sendHeader(OutputStream pOut, String pKey, String pValue) throws IOException {
        pOut.write(toHTTPBytes(pKey + ": " + pValue + "\r\n"));
    }

    @SuppressWarnings("unchecked")
    private void sendRequestHeaders(OutputStream pOut) throws IOException {
        pOut.write(("POST " + uri + " HTTP/1.0\r\n").getBytes(StandardCharsets.US_ASCII));
        for (Object o : headers.entrySet()) {
            Map.Entry<String, Object> entry = (Map.Entry) o;
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof String) {
                sendHeader(pOut, key, (String) value);
            } else {
                List<Object> list = (List) value;
                for (Object item : list) {
                    sendHeader(pOut, key, (String) item);
                }
            }
        }
        pOut.write(toHTTPBytes("\r\n"));
    }

    @Override
    protected boolean isResponseGzipCompressed(XmlRpcStreamRequestConfig pConfig) {
        return responseGzipCompressed;
    }

    @Override
    protected InputStream getInputStream() throws XmlRpcException {
        final byte[] buffer = new byte[2048];
        try {
            // If reply timeout specified, set the socket timeout accordingly
            if (config.getReplyTimeout() != 0)
                socket.setSoTimeout(config.getReplyTimeout());
            input = new BufferedInputStream(socket.getInputStream());
            // start reading  server response headers
            String line = HttpUtil.readLine(input, buffer);
            StringTokenizer tokens = new StringTokenizer(line);
            tokens.nextToken(); // Skip HTTP version
            String statusCode = tokens.nextToken();
            String statusMsg = tokens.nextToken("\n\r");
            final int code;
            try {
                code = Integer.parseInt(statusCode);
            } catch (NumberFormatException e) {
                throw new XmlRpcClientException("Server returned invalid status code: "
                        + statusCode + " " + statusMsg, null);
            }
            if (code < 200  ||  code > 299) {
                throw new XmlRpcHttpTransportException(code, statusMsg);
            }
            int contentLength = -1;
            for (;;) {
                line = HttpUtil.readLine(input, buffer);
                if ("".equals(line)) {
                    break;
                }
                line = line.toLowerCase();
                if (line.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                } else if (line.startsWith("content-encoding:")) {
                    responseGzipCompressed = HttpUtil.isUsingGzipEncoding(line.substring("content-encoding:".length()));
                }
            }
            InputStream result;
            if (contentLength == -1) {
                result = input;
            } else {
                result = new LimitedInputStream(input, contentLength);
            }
            return result;
        } catch (IOException e) {
            throw new XmlRpcClientException("Failed to read server response: " + e.getMessage(), e);
        }
    }

    @Override
    protected boolean isUsingByteArrayOutput(XmlRpcHttpClientConfig pConfig) {
        boolean result = super.isUsingByteArrayOutput(pConfig);
        if (!result) {
            throw new IllegalStateException("The Content-Length header is required with HTTP/1.0, and HTTP/1.1 is unsupported by the Lite HTTP Transport.");
        }
        return result;
    }

    @Override
    protected void writeRequest(ReqWriter pWriter) throws XmlRpcException, IOException, SAXException {
        pWriter.write(getOutputStream());
    }
}
