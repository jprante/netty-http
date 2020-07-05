package org.xbib.netty.http.common.mime;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A MIME multi part message parser (RFC 2046).
 */
public class MimeMultipartParser {

    private final String contentType;

    private final ByteBuf payload;

    private byte[] boundary;

    private String type;

    private String subType;

    public MimeMultipartParser(String contentType, ByteBuf payload) {
        this.contentType = contentType;
        this.payload = payload;
        if (contentType != null) {
            int pos = contentType.indexOf(';');
            this.type = pos >= 0 ? contentType.substring(0, pos) : contentType;
            this.type = type.trim().toLowerCase();
            this.subType = type.startsWith("multipart") ? type.substring(10).trim() : null;
            Map<String, String> m = parseHeaderLine(contentType);
            this.boundary = m.containsKey("boundary") ?
                    m.get("boundary").getBytes(StandardCharsets.US_ASCII) : null;
        }
    }

    public String type() {
        return type;
    }

    public String subType() {
        return subType;
    }

    public void parse(MimeMultipartListener listener) throws IOException {
        if (boundary == null) {
            return;
        }
        // Assumption: header is in 8 bytes (ISO-8859-1). Convert to Unicode.
        StringBuilder sb = new StringBuilder();
        boolean inHeader = true;
        boolean inBody = false;
        Integer start = null;
        Map<String, String> headers = new LinkedHashMap<>();
        int eol = 0;
        byte[] payloadBytes = payload.array();
        for (int i = 0; i < payloadBytes.length; i++) {
            byte b = payloadBytes[i];
            if (inHeader) {
                switch (b) {
                    case '\r':
                        break;
                    case '\n':
                        if (sb.length() > 0) {
                            String[] s = sb.toString().split(":");
                            String k = s[0];
                            String v = s[1];
                            if (!k.startsWith("--")) {
                                headers.put(k.toLowerCase(Locale.ROOT), v.trim());
                            }
                            eol = 0;
                            sb.setLength(0);
                        } else {
                            eol++;
                            if (eol >= 1) {
                                eol = 0;
                                sb.setLength(0);
                                inHeader = false;
                                inBody = true;
                            }
                        }
                        break;
                    default:
                        eol = 0;
                        sb.append(b);
                        break;
                }
            }
            if (inBody) {
                int len = headers.containsKey("content-length") ?
                        Integer.parseInt(headers.get("content-length")) : -1;
                if (len > 0) {
                    inBody = false;
                    inHeader = true;
                } else {
                    if (b != '\r' && b != '\n') {
                        start = i;
                    }
                    if (start != null) {
                        i = indexOf(payloadBytes, boundary, start, payloadBytes.length);
                        if (i == -1) {
                            throw new IOException("boundary not found");
                        }
                        int l = i - start;
                        if (l > 4) {
                            l = l - 4;
                        }
                        //BytesReference body = new BytesArray(payloadBytes, start, l)
                        ByteBuf body = payload.retainedSlice(start, l);
                        Map<String, String> m = new LinkedHashMap<>();
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            m.putAll(parseHeaderLine(entry.getValue()));
                        }
                        headers.putAll(m);
                        if (listener != null) {
                            listener.handle(type, subType, new MimePart(headers, body));
                        }
                        inBody = false;
                        inHeader = true;
                        headers = new LinkedHashMap<>();
                        start = null;
                        eol = -1;
                    }
                }
            }
        }
    }

    private Map<String, String> parseHeaderLine(String line) {
        Map<String, String> params = new LinkedHashMap<>();
        int pos = line.indexOf(";");
        String spec = line.substring(pos + 1);
        if (pos < 0) {
            return params;
        }
        String key = "";
        String value;
        boolean inKey = true;
        boolean inString = false;
        int start = 0;
        int i;
        for (i = 0; i < spec.length(); i++) {
            switch (spec.charAt(i)) {
                case '=':
                    if (inKey) {
                        key = spec.substring(start, i).trim().toLowerCase();
                        start = i + 1;
                        inKey = false;
                    } else if (!inString) {
                        throw new IllegalArgumentException(contentType + " value has illegal character '=' at " + i + ": " + spec);
                    }
                    break;
                case ';':
                    if (inKey) {
                        if (spec.substring(start, i).trim().length() > 0) {
                            throw new IllegalArgumentException(contentType + " parameter missing value at " + i + ": " + spec);
                        } else {
                            throw new IllegalArgumentException(contentType + " parameter key has illegal character ';' at " + i + ": " + spec);
                        }
                    } else if (!inString) {
                        value = spec.substring(start, i).trim();
                        params.put(key, value);
                        key = null;
                        start = i + 1;
                        inKey = true;
                    }
                    break;
                case '"':
                    if (inKey) {
                        throw new IllegalArgumentException(contentType + " key has illegal character '\"' at " + i + ": " + spec);
                    } else if (inString) {
                        value = spec.substring(start, i).trim();
                        params.put(key, value);
                        key = null;
                        for (i++; i < spec.length() && spec.charAt(i) != ';'; i++) {
                            if (!Character.isWhitespace(spec.charAt(i))) {
                                throw new IllegalArgumentException(contentType + " value has garbage after quoted string at " + i + ": " + spec);
                            }
                        }
                        start = i + 1;
                        inString = false;
                        inKey = true;
                    } else {
                        if (spec.substring(start, i).trim().length() > 0) {
                            throw new IllegalArgumentException(contentType + " value has garbage before quoted string at " + i + ": " + spec);
                        }
                        start = i + 1;
                        inString = true;
                    }
                    break;
            }
        }
        if (inKey) {
            if (pos > start && spec.substring(start, i).trim().length() > 0) {
                throw new IllegalArgumentException(contentType + " missing value at " + i + ": " + spec);
            }
        } else if (!inString) {
            value = spec.substring(start, i).trim();
            params.put(key, value);
        } else {
            throw new IllegalArgumentException(contentType + " has an unterminated quoted string: " + spec);
        }
        return params;
    }

    private static int indexOf(byte[] array, byte[] target, int start, int end) {
        if (target.length == 0) {
            return 0;
        }
        outer:
        for (int i = start; i < end - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
