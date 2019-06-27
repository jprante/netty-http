package org.xbib.netty.http.common.cookie;

import java.util.BitSet;

public final class CookieUtil {

    private static final BitSet VALID_COOKIE_NAME_OCTETS;

    private static final BitSet VALID_COOKIE_VALUE_OCTETS;

    private static final BitSet VALID_COOKIE_ATTRIBUTE_VALUE_OCTETS;

    static {
        int[] separators = new int[] {
                '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}', ' ', '\t'
        };
        VALID_COOKIE_NAME_OCTETS = validCookieNameOctets(separators);
        VALID_COOKIE_VALUE_OCTETS = validCookieValueOctets();
        VALID_COOKIE_ATTRIBUTE_VALUE_OCTETS = validCookieAttributeValueOctets();
    }

    /**
     * Horizontal space
     */
    public static final char SP = 32;

    /**
     * Equals '='
     */
    public static final char EQUALS = 61;

    /**
     * Semicolon ';'
     */
    public static final char SEMICOLON = 59;

    /**
     * Double quote '"'
     */
    public static final char DOUBLE_QUOTE = 34;

    private CookieUtil() {
    }

    private static BitSet validCookieNameOctets(int[] separators) {
        BitSet bits = new BitSet();
        for (int i = 32; i < 127; i++) {
            bits.set(i);
        }
        for (int separator : separators) {
            bits.set(separator, false);
        }
        return bits;
    }

    // cookie-octet = %x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E
    // US-ASCII characters excluding CTLs, whitespace, DQUOTE, comma, semicolon, and backslash
    private static BitSet validCookieValueOctets() {
        BitSet bits = new BitSet();
        bits.set(0x21);
        for (int i = 0x23; i <= 0x2B; i++) {
            bits.set(i);
        }
        for (int i = 0x2D; i <= 0x3A; i++) {
            bits.set(i);
        }
        for (int i = 0x3C; i <= 0x5B; i++) {
            bits.set(i);
        }
        for (int i = 0x5D; i <= 0x7E; i++) {
            bits.set(i);
        }
        return bits;
    }

    // path-value = <any CHAR except CTLs or ";">
    private static BitSet validCookieAttributeValueOctets() {
        BitSet bits = new BitSet();
        for (int i = 32; i < 127; i++) {
            bits.set(i);
        }
        // ';' = 59
        bits.set(59, false);
        return bits;
    }

    /**
     * @param buf a buffer where some cookies were maybe encoded
     * @return the buffer String without the trailing separator, or null if no cookie was appended.
     */
    public static String stripTrailingSeparatorOrNull(StringBuilder buf) {
        return buf.length() == 0 ? null : stripTrailingSeparator(buf);
    }

    public static String stripTrailingSeparator(StringBuilder buf) {
        if (buf.length() > 0) {
            buf.setLength(buf.length() - 2);
        }
        return buf.toString();
    }

    public static void add(StringBuilder sb, String name, long val) {
        sb.append(name)
        .append(EQUALS)
        .append(val)
        .append(SEMICOLON)
        .append(SP);
    }

    public static void add(StringBuilder sb, String name, String val) {
        sb.append(name)
        .append(EQUALS)
        .append(val)
        .append(SEMICOLON)
        .append(SP);
    }

    public static void add(StringBuilder sb, String name) {
        sb.append(name)
        .append(SEMICOLON)
        .append(SP);
    }

    public static void addQuoted(StringBuilder sb, String name, String val) {
        if (val == null) {
            val = "";
        }
        sb.append(name)
        .append(EQUALS)
        .append(DOUBLE_QUOTE)
        .append(val)
        .append(DOUBLE_QUOTE)
        .append(SEMICOLON)
        .append(SP);
    }

    public static int firstInvalidCookieNameOctet(CharSequence cs) {
        return firstInvalidOctet(cs, VALID_COOKIE_NAME_OCTETS);
    }

    public static int firstInvalidCookieValueOctet(CharSequence cs) {
        return firstInvalidOctet(cs, VALID_COOKIE_VALUE_OCTETS);
    }

    public static int firstInvalidOctet(CharSequence cs, BitSet bits) {
        for (int i = 0; i < cs.length(); i++) {
            char c = cs.charAt(i);
            if (!bits.get(c)) {
                return i;
            }
        }
        return -1;
    }

    public static CharSequence unwrapValue(CharSequence cs) {
        final int len = cs.length();
        if (len > 0 && cs.charAt(0) == ('"')) {
            if (len >= 2 && cs.charAt(len - 1) == ('"')) {
                return len == 2 ? "" : cs.subSequence(1, len - 1);
            } else {
                return null;
            }
        }
        return cs;
    }

    public static String validateAttributeValue(String name, String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        if (value.isEmpty()) {
            return null;
        }
        int i = firstInvalidOctet(value, VALID_COOKIE_ATTRIBUTE_VALUE_OCTETS);
        if (i != -1) {
            throw new IllegalArgumentException(name + " contains the prohibited characters: " + (value.charAt(i)));
        }
        return value;
    }
}
