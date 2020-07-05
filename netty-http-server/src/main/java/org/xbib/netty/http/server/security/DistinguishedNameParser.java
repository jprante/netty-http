package org.xbib.netty.http.server.security;

import javax.security.auth.x500.X500Principal;

/**
 * A distinguished name (DN) parser.
 * This parser only supports extracting a string value from a DN.
 * It doesn't support values in the hex-string style.
 *
 * Taken from okhttp
 */
public final class DistinguishedNameParser {

    private final String dn;

    private final int length;

    private int pos;

    private int beg;

    private int end;

    private int cur;

    private char[] chars;

    public DistinguishedNameParser(X500Principal principal) {
        this.dn = principal.getName(X500Principal.RFC2253);
        this.length = this.dn.length();
    }

    private String nextAT() {
        while (pos < length && chars[pos] == ' ') {
            pos++;
        }
        if (pos == length) {
            return null;
        }
        beg = pos;
        pos++;
        while (pos < length && chars[pos] != '=' && chars[pos] != ' ') {
            pos++;
        }
        if (pos >= length) {
            throw new IllegalStateException("Unexpected end of DN: " + dn);
        }
        end = pos;
        if (chars[pos] == ' ') {
            while (pos < length && chars[pos] != '=' && chars[pos] == ' ') {
                pos++;
            }
            if (chars[pos] != '=' || pos == length) {
                throw new IllegalStateException("Unexpected end of DN: " + dn);
            }
        }
        pos++;
        while (pos < length && chars[pos] == ' ') {
            pos++;
        }
        if ((end - beg > 4) && (chars[beg + 3] == '.') &&
                (chars[beg] == 'O' || chars[beg] == 'o') &&
                (chars[beg + 1] == 'I' || chars[beg + 1] == 'i') &&
                (chars[beg + 2] == 'D' || chars[beg + 2] == 'd')) {
            beg += 4;
        }
        return new String(chars, beg, end - beg);
    }

    private String quotedAV() {
        pos++;
        beg = pos;
        end = beg;
        while (true) {
            if (pos == length) {
                throw new IllegalStateException("Unexpected end of DN: " + dn);
            }
            if (chars[pos] == '"') {
                pos++;
                break;
            } else if (chars[pos] == '\\') {
                chars[end] = getEscaped();
            } else {
                chars[end] = chars[pos];
            }
            pos++;
            end++;
        }
        while (pos < length && chars[pos] == ' ') {
            pos++;
        }
        return new String(chars, beg, end - beg);
    }

    private String hexAV() {
        if (pos + 4 >= length) {
            throw new IllegalStateException("Unexpected end of DN: " + dn);
        }
        beg = pos;
        pos++;
        while (true) {
            if (pos == length || chars[pos] == '+' || chars[pos] == ','
                    || chars[pos] == ';') {
                end = pos;
                break;
            }
            if (chars[pos] == ' ') {
                end = pos;
                pos++;
                while (pos < length && chars[pos] == ' ') {
                    pos++;
                }
                break;
            } else if (chars[pos] >= 'A' && chars[pos] <= 'F') {
                chars[pos] += 32;
            }
            pos++;
        }
        int hexLen = end - beg;
        if (hexLen < 5 || (hexLen & 1) == 0) {
            throw new IllegalStateException("Unexpected end of DN: " + dn);
        }
        return new String(chars, beg, hexLen);
    }

    private String escapedAV() {
        beg = pos;
        end = pos;
        while (true) {
            if (pos >= length) {
                return new String(chars, beg, end - beg);
            }
            switch (chars[pos]) {
                case '+':
                case ',':
                case ';':
                    return new String(chars, beg, end - beg);
                case '\\':
                    chars[end++] = getEscaped();
                    pos++;
                    break;
                case ' ':
                    cur = end;
                    pos++;
                    chars[end++] = ' ';
                    for (; pos < length && chars[pos] == ' '; pos++) {
                        chars[end++] = ' ';
                    }
                    if (pos == length ||
                            chars[pos] == ',' ||
                            chars[pos] == '+' ||
                            chars[pos] == ';') {
                        return new String(chars, beg, cur - beg);
                    }
                    break;
                default:
                    chars[end++] = chars[pos];
                    pos++;
            }
        }
    }

    private char getEscaped() {
        pos++;
        if (pos == length) {
            throw new IllegalStateException("Unexpected end of DN: " + dn);
        }
        switch (chars[pos]) {
            case '"':
            case '\\':
            case ',':
            case '=':
            case '+':
            case '<':
            case '>':
            case '#':
            case ';':
            case ' ':
            case '*':
            case '%':
            case '_':
                return chars[pos];
            default:
                return getUTF8();
        }
    }

    private char getUTF8() {
        int res = getByte(pos);
        pos++;
        if (res < 128) {
            return (char) res;
        } else if (res >= 192 && res <= 247) {
            int count;
            if (res <= 223) {
                count = 1;
                res = res & 0x1F;
            } else if (res <= 239) {
                count = 2;
                res = res & 0x0F;
            } else {
                count = 3;
                res = res & 0x07;
            }
            int b;
            for (int i = 0; i < count; i++) {
                pos++;
                if (pos == length || chars[pos] != '\\') {
                    return 0x3F;
                }
                pos++;
                b = getByte(pos);
                pos++;
                if ((b & 0xC0) != 0x80) {
                    return 0x3F;
                }
                res = (res << 6) + (b & 0x3F);
            }
            return (char) res;
        } else {
            return 0x3F;
        }
    }

    private int getByte(int position) {
        if (position + 1 >= length) {
            throw new IllegalStateException("Malformed DN: " + dn);
        }
        int b1, b2;
        b1 = chars[position];
        if (b1 >= '0' && b1 <= '9') {
            b1 = b1 - '0';
        } else if (b1 >= 'a' && b1 <= 'f') {
            b1 = b1 - 87;
        } else if (b1 >= 'A' && b1 <= 'F') {
            b1 = b1 - 55;
        } else {
            throw new IllegalStateException("Malformed DN: " + dn);
        }
        b2 = chars[position + 1];
        if (b2 >= '0' && b2 <= '9') {
            b2 = b2 - '0';
        } else if (b2 >= 'a' && b2 <= 'f') {
            b2 = b2 - 87;
        } else if (b2 >= 'A' && b2 <= 'F') {
            b2 = b2 - 55;
        } else {
            throw new IllegalStateException("Malformed DN: " + dn);
        }
        return (b1 << 4) + b2;
    }

    public String findMostSpecific(String attributeType) {
        pos = 0;
        beg = 0;
        end = 0;
        cur = 0;
        chars = dn.toCharArray();
        String attType = nextAT();
        if (attType == null) {
            return null;
        }
        while (true) {
            String attValue = "";
            if (pos == length) {
                return null;
            }
            switch (chars[pos]) {
                case '"':
                    attValue = quotedAV();
                    break;
                case '#':
                    attValue = hexAV();
                    break;
                case '+':
                case ',':
                case ';':
                    break;
                default:
                    attValue = escapedAV();
            }
            if (attributeType.equalsIgnoreCase(attType)) {
                return attValue;
            }
            if (pos >= length) {
                return null;
            }
            if (chars[pos] != ',' && chars[pos] != ';') {
                if (chars[pos] != '+') {
                    throw new IllegalStateException("Malformed DN: " + dn);
                }
            }
            pos++;
            attType = nextAT();
            if (attType == null) {
                throw new IllegalStateException("Malformed DN: " + dn);
            }
        }
    }
}
