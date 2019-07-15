package org.xbib.netty.http.xmlrpc.common.util;

import org.xbib.netty.http.xmlrpc.common.XmlRpcHttpRequestConfigImpl;
import org.xbib.netty.http.xmlrpc.common.XmlRpcStreamConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.StringTokenizer;

/**
 * Provides utility functions useful in HTTP communications
 */
public class HttpUtil {

    /**
     * Creates the Base64 encoded credentials for HTTP Basic Authentication.
     * @param pUser User name, or null, if no Basic Authentication is being used.
     * @param pPassword Users password, or null, if no Basic Authentication is being used.
     * @param pEncoding Encoding being used for conversion of the credential string into a byte array.
     * @return Base64 encoded credentials, for use in the HTTP header
     * @throws UnsupportedEncodingException The encoding <code>pEncoding</code> is invalid.
     */
    public static String encodeBasicAuthentication(String pUser, String pPassword, String pEncoding)
            throws UnsupportedEncodingException {
        if (pUser == null) {
            return null;
        }
        final String s = pUser + ':' + pPassword;
        if (pEncoding == null) {
            pEncoding = XmlRpcStreamConfig.UTF8_ENCODING;
        }
        final byte[] bytes = s.getBytes(pEncoding);
        return Base64.getEncoder().encodeToString(s.getBytes(pEncoding));
    }

    /**
     * Returns, whether the HTTP header value <code>pHeaderValue</code>
     * indicates, that GZIP encoding is used or may be used.
     * @param pHeaderValue The HTTP header value being parsed. This is typically
     * the value of "Content-Encoding", or "Accept-Encoding".
     * @return True, if the header value suggests that GZIP encoding is or may
     * be used.
     */
    public static boolean isUsingGzipEncoding(String pHeaderValue) {
        if (pHeaderValue == null) {
            return false;
        }
        for (StringTokenizer st = new StringTokenizer(pHeaderValue, ",");  st.hasMoreTokens();  ) {
            String encoding = st.nextToken();
            int offset = encoding.indexOf(';');
            if (offset >= 0) {
                encoding = encoding.substring(0, offset);
            }
            if ("gzip".equalsIgnoreCase(encoding.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns, whether the HTTP header value <code>pHeaderValue</code>
     * indicates, that another encoding than "identity" is used.
     * This is typically the value of "Transfer-Encoding", or "TE".
     * @return Null, if the transfer encoding in use is "identity".
     *   Otherwise, another transfer encoding. 
     */
    public static String getNonIdentityTransferEncoding(String pHeaderValue) {
        if (pHeaderValue == null) {
            return null;
        }
        for (StringTokenizer st = new StringTokenizer(pHeaderValue, ",");  st.hasMoreTokens();  ) {
            String encoding = st.nextToken();
            int offset = encoding.indexOf(';');
            if (offset >= 0) {
                encoding = encoding.substring(0, offset);
            }
            if (!"identity".equalsIgnoreCase(encoding.trim())) {
                return encoding.trim();
            }
        }
        return null;
    }

    /**
     * Returns, whether the HTTP header values in <code>pValues</code>
     * indicate, that GZIP encoding is used or may be used.
     * @param pValues The HTTP header values being parsed. These are typically
     * the values of "Content-Encoding", or "Accept-Encoding".
     * @return True, if the header values suggests that GZIP encoding is or may
     * be used.
     */
    public static boolean isUsingGzipEncoding(Enumeration<String> pValues) {
        if (pValues != null) {
            while (pValues.hasMoreElements()) {
                if (isUsingGzipEncoding(pValues.nextElement())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Reads a header line from the input stream <code>pIn</code>
     * and converts it into a string.
     * @param pIn The input stream being read.
     * @param pBuffer A buffer being used for temporary storage.
     * The buffers length is a limit of the header lines length.
     * @return Next header line or null, if no more header lines
     * are available.
     * @throws IOException Reading the header line failed.
     */
    public static String readLine(InputStream pIn, byte[] pBuffer) throws IOException {
        int next;
        int count = 0;
        while (true) {
            next = pIn.read();
            if (next < 0 || next == '\n') {
                break;
            }
            if (next != '\r') {
                pBuffer[count++] = (byte) next;
            }
            if (count >= pBuffer.length) {
                throw new IOException ("HTTP Header too long");
            }
        }
        return new String(pBuffer, 0, count, "US-ASCII");
    }

    /**
     * Parses an "Authorization" header and adds the username and password
     * to <code>pConfig</code>.
     * @param pConfig The request configuration being created.
     * @param pLine The header being parsed, including the "basic" part.
     */
    public static void parseAuthorization(XmlRpcHttpRequestConfigImpl pConfig, String pLine) {
        if (pLine == null) {
            return;
        }
        pLine = pLine.trim();
        StringTokenizer st = new StringTokenizer(pLine);
        if (!st.hasMoreTokens()) {
            return;
        }
        String type = st.nextToken();
        if (!"basic".equalsIgnoreCase(type)) {
            return;
        }
        if (!st.hasMoreTokens()) {
            return;
        }
        String auth = st.nextToken();
        try {
            byte[] c = Base64.getDecoder().decode(auth);
            String enc = pConfig.getBasicEncoding();
            if (enc == null) {
                enc = XmlRpcStreamConfig.UTF8_ENCODING;
            }
            String str = new String(c, enc);
            int col = str.indexOf(':');
            if (col >= 0) {
                pConfig.setBasicUserName(str.substring(0, col));
                pConfig.setBasicPassword(str.substring(col+1));
            }
        } catch (Throwable ignore) {
        }
    }
}
