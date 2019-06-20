package org.xbib.netty.http.server.util;

public class HtmlUtils {

    /**
     * Returns an HTML-escaped version of the given string for safe display
     * within a web page. The characters '&amp;', '&gt;' and '&lt;' must always
     * be escaped, and single and double quotes must be escaped within
     * attribute values; this method escapes them always. This method can
     * be used for generating both HTML and XHTML valid content.
     *
     * @param s the string to escape
     * @return the escaped string
     * @see <a href="http://www.w3.org/International/questions/qa-escapes">The W3C FAQ</a>
     */
    public static String escapeHTML(String s) {
        int len = s.length();
        StringBuilder es = new StringBuilder(len + 30);
        int start = 0;
        for (int i = 0; i < len; i++) {
            String ref = null;
            switch (s.charAt(i)) {
                case '&':
                    ref = "&amp;";
                    break;
                case '>':
                    ref = "&gt;";
                    break;
                case '<':
                    ref = "&lt;";
                    break;
                case '"':
                    ref = "&quot;";
                    break;
                case '\'':
                    ref = "&#39;";
                    break;
                default:
                    break;
            }
            if (ref != null) {
                es.append(s, start, i).append(ref);
                start = i + 1;
            }
        }
        return start == 0 ? s : es.append(s.substring(start)).toString();
    }
}
